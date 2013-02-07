/**
 * [Version Information] v 1.2 (25 Nov 2012) the 16 bits PCM signal transforms
 * into a range of -1f to 1f for AEC process. All internal variable types are
 * FLOAT.
 *
 * v 1.4 (15 Jan 2013) update Mju one time for every sample in a frame block
 *
 * V 1.5 (the end of Feb 2013) 1. hamming window, and band-pass filter. 2.
 * overlap-save 3. error detection and AEC restarting.
 *
 * REFERENCE "I guess it depends on what you are processing. If you are
 * calculating the FFT over a large duration you might find that it does take a
 * while depending on how many frequency points you are wanting. However, in
 * most cases for audio it is considered non-stationary (that is the signals
 * mean and variance changes to much over time), so taking one large FFT
 * (Periodogram PSD estimate) is not an accurate representation. Alternatively
 * you could use Short-time Fourier transform, whereby you break the signal up
 * into smaller frames and calculate the FFT. The frame size varies depending on
 * how quickly the statistics change, for speech it is usually 20-40ms, for
 * music I assume it is slightly higher. This method is good if you are sampling
 * from the microphone, because it allows you to buffer each frame at a time,
 * calculate the fft and give what the user feels is "real time" interaction.
 * Because 20ms is quick, because we can't really perceive a time difference
 * that small."
 */

/*
 * "the fastest FFT algorithms generally occur when  is a power of 2.
 * In practical audio signal processing, we routinely zero-pad our FFT input buffers
 * to the next power of 2 in length (thereby interpolating our spectra somewhat)
 * in order to enjoy the power-of-2 speed advantage."
 *
 *  "sampled sound system can only handle sound data where the maximum frequency
 * content is half that of the sample rate, which is called the Nyquist limit."
 *
 */
package jaec;

/**
 *
 * The echo cancellation algorithm is mainly based on the paper:
 *
 * "Valin, J.-M., On Adjusting the Learning Rate in Frequency Domain Echo
 * Cancellation With Double-Talk. IEEE Transactions on Audio, Speech and
 * Language Processing, Vol. 15, No. 3, pp. 1030-1034, 2007".
 *
 * JTransformation 2.4 is used here for the Fast Fourier Transform in this
 * algorithm. Half complex FFT is applied in order to reduce the cost of
 * computing. In that paper, acoustic signals as long as echo reverberation
 * length are processed via FFT, but this will slow down the whole procedure and
 * it can not achieve the speed for real-time processing. So the algorithm is
 * modified and only the latest few signals are transfored to frequency domain
 * and calcualte the adjusted learning rate. The size of these few signals must
 * be the power of 2. According to a theory that around 30 ms is the most
 * suitable length of acoustic signal which can represent the statistical
 * characeteristics of a speech. So 256 is selected as the size of FFT in a case
 * of 16000 Hz sampling rate.
 *
 *
 * @author Jie Yang (Dublin, January, 2013)
 */
public class JAEC {

    /**
     * fftSize 1. size of FFT must be the power of 2 in order to apply fast
     * dscrect transforms. 2. "break the signal up into smaller frames and
     * calculate the FFT. The frame size varies depending on how quickly the
     * statistics change, for speech it is usually 20-40ms" So the length of FFT
     * should be around 320 samples.
     */
    private final int frameSize = 512;
    /**
     * mjuMax mentioned in paper, 0.5f or 1 in paper, bigger one can lead to
     * quicker convergence
     */
    private final float mjuMax_F = 0.5f;//0.5f;
    private final float SHORT_RANGE = 32768.0f; // max value of SHORT type, it is used for nomalization of SHORT.
    private float sample_rate;
    // change Public to private after testing.
    public float mju;
    public float eit; //leakage estimation.
    private float gama;
    private float beta0;
    private float betaMax;
    private int tailLength; //number of weights
    private float[] weights; // w0 is relevant to the latest frame in query
    // use float instead of short, because CAST will kill alot of time
    private float[] Xs_Frame_Buffer_T; // buffer for a complete far-end signal frame.
    private float[] Ds_Frame_Buffer_T; // buffer for a complete near-end signal frame.
    private float[] Ds_Frame_T; // the latest entire near-end frame.
    private XFIFOArray Xs_T; // far-end frame query (Include: frame and tail)
    private float[] Ys_T; // echo query
    private float[] Ys_HalfCompex_F;// in complex[real, imag],[real, imag]...
    private float[] Ys_pre_HalfCompex_F;// in complex[real, imag],[real, imag]...
    private float[] Es_T; // output query in time domain
    private float[] Es_HalfCompex_F;// in complex[real, imag],[real, imag]...
    private float[] Es_pre_HalfCompex_F;// in complex[real, imag],[real, imag]...
    private float[] mjus_overlap_HalfComplex_F; // miu query in frequency domain
    private float[] py_HalfComplex_F;
    private float[] pe_HalfComplex_F;
    private float[] rey_HalfComplex_F;
    private float[] ryy_HalfComplex_F;
    private FFT_HalfComplex fft;
    /**
     * Variables for statistical records of AEC
     *
     * NOTE: 32-bit INT can have a maximal value of 2,147,483,647, and then it
     * will go to -2,147,483,648 after adding one more.
     */
    private int totalCount = 0;
    private int initialCount = 0;
    private int countInBuffer = 0;
    /**
     * AEC Pre-Filter
     */
    private JAEC_Filter filter;
    private final int lowPassBand = 50;//Hz, for band-pass filter
    private final int highPassBand = 5000;//Hz, for band-pass filter
    /**
     * Reset Mechanism for Error
     */
    public int errorCount = 0;
    private final int restartThreshold = 2; // max number of error to trigger restarting.
    private boolean justRestart = false;
    /**
     * variable for E process after AEC
     */
    private int countInE_Buffer = 0;
    private float[] e1 = new float[this.frameSize];
    private float[] e2 = new float[this.frameSize];

    /**
     * The constructor of this class.
     *
     * @param echo_path_length: the length of echo reverberation time, counted
     * by the number of samples.
     * @param sample_rate : rate of sampling, the number of samples in one
     * second.
     */
    public JAEC(int tail_length, float sample_rate) {

        /**
         * the length should be compatible with the performance of local PC, 
         * echo path distance and sample rate. but it is fixed to 2000, in order to fit to the
         * most scenarios. it could be dynamically changed by some automatic
         * detection of PC performance and echo path distance, which would be a
         * potential future work.
         */
        //In orde to config the JAEC easily
        tail_length = 2000;//8192;


        this.tailLength = tail_length;
        this.sample_rate = sample_rate;
        //initial FFT convert
        this.fft = new FFT_HalfComplex(this.frameSize);

        /*
         * mdf.c
         * gama: spectrum average (efinition from Speec C code)
         * equation:
         * st->spec_average = DIV32_16(SHL32(EXTEND32(st->frame_size), 15), st->sampling_rate);
         */
        this.gama = this.frameSize / this.sample_rate;

        /**
         * mdf.c st->beta0 = (2.0f * st->frame_size)/st->sampling_rate;
         * st->beta_max = (.5f * st->frame_size)/st->sampling_rate;
         */
        this.beta0 = 2f * this.frameSize / this.sample_rate;
        /**
         * Beta_max not in paper, but used in Speex. In order to use betaMax,
         * the relevant IF statement should be uncomment. Speex code: private
         * double betaMax = 0.5*this.frame_size/ this.sample_rate; where
         * frame_size is the amount of data (in samples) you want to process at
         * once and filter_length is the length (in samples) of the echo
         * canceling filter you want to use (also known as tail length). It is
         * recommended to use a frame size in the order of 20 ms (or equal to
         * the codec frame size) and make sure it is easy to perform an FFT of
         * that size (powers of two are better than prime sizes).
         */
        this.betaMax = 0.5f * this.frameSize / this.sample_rate;

        //initial weight value with Zero.
        this.weights = new float[this.tailLength];
        for (int i = 0; i < this.tailLength; i++) {
            this.weights[i] = 0;
        }

        this.Xs_Frame_Buffer_T = new float[this.frameSize];
        this.Ds_Frame_Buffer_T = new float[this.frameSize];
        this.Ds_Frame_T = new float[this.frameSize];
        this.Xs_T = new XFIFOArray(this.frameSize, this.tailLength);
        this.Ys_T = new float[this.frameSize];
        //this.fftNu*2(complex)/2(half complex) in JTransforms Format
        /**
         * Re[0]=a[0]; Im[0]=0;
         *
         * Re[k] = a[2*k], 0<k<n/2 Im[k] = a[2*k+1] 0<k<n/2
         *
         * Re[n/2]=a[1]; Im[n/2]=0;
         */
        this.Ys_HalfCompex_F = new float[this.frameSize];
        this.Ys_pre_HalfCompex_F = new float[this.frameSize];
        this.Es_T = new float[this.frameSize];
        this.Es_HalfCompex_F = new float[this.frameSize];
        this.Es_pre_HalfCompex_F = new float[this.frameSize];
        //only calcuate and store the latest mju in frequency domain for inverse FFT. 
        this.mjus_overlap_HalfComplex_F = new float[this.frameSize / 2 + 1];
        for (int i = 0; i < this.frameSize / 2 + 1; i++) {
            this.mjus_overlap_HalfComplex_F[i] = 0.25f;
        }
        //initialise Mju from initial frequency domain.
        this.mju = this.getMju();

        this.pe_HalfComplex_F = new float[this.frameSize / 2 + 1];
        this.py_HalfComplex_F = new float[this.frameSize / 2 + 1];

        this.rey_HalfComplex_F = new float[this.frameSize / 2 + 1];
        this.ryy_HalfComplex_F = new float[this.frameSize / 2 + 1];

        this.filter = new JAEC_Filter(this.lowPassBand, this.highPassBand, this.sample_rate, this.frameSize);

        System.out.println("INFO: AEC Initialisation \n"
                + "         frame(fft):" + this.frameSize + ",\n"
                + "         tail(weights):" + this.tailLength + ",\n"
                + "         sample rate:" + (int) this.sample_rate + ".");
    }

    /**
     * function to restart AEC. (change Public to private after testing.)
     */
    public void reset() {
        //initial weight value with Zero.
        this.weights = new float[this.tailLength];
        for (int i = 0; i < this.tailLength; i++) {
            this.weights[i] = 0;
        }

        this.Xs_Frame_Buffer_T = new float[this.frameSize];
        this.Ds_Frame_Buffer_T = new float[this.frameSize];
        this.Ds_Frame_T = new float[this.frameSize];
        this.Xs_T.reset();
        this.Ys_T = new float[this.frameSize];
        //this.fftNu*2(complex)/2(half complex) in JTransforms Format
        /**
         * Re[0]=a[0]; Im[0]=0;
         *
         * Re[k] = a[2*k], 0<k<n/2 Im[k] = a[2*k+1] 0<k<n/2
         *
         * Re[n/2]=a[1]; Im[n/2]=0;
         */
        this.Ys_HalfCompex_F = new float[this.frameSize];
        this.Ys_pre_HalfCompex_F = new float[this.frameSize];
        this.Es_T = new float[this.frameSize];
        this.Es_HalfCompex_F = new float[this.frameSize];
        this.Es_pre_HalfCompex_F = new float[this.frameSize];
        //only calcuate and store the latest mju in frequency domain for inverse FFT. 
        this.mjus_overlap_HalfComplex_F = new float[this.frameSize / 2 + 1];
        for (int i = 0; i < this.frameSize / 2 + 1; i++) {
            this.mjus_overlap_HalfComplex_F[i] = 0.25f;
        }
        //initialise Mju from initial frequency domain.
        this.mju = this.getMju();
        this.eit = 0;

        this.pe_HalfComplex_F = new float[this.frameSize / 2 + 1];
        this.py_HalfComplex_F = new float[this.frameSize / 2 + 1];

        this.rey_HalfComplex_F = new float[this.frameSize / 2 + 1];
        this.ryy_HalfComplex_F = new float[this.frameSize / 2 + 1];

        this.errorCount = 0;
        this.totalCount = 0;
        this.countInBuffer = 0;

        //variables for E pro-AEC-process
        this.countInE_Buffer = 0;
        this.e1 = new float[this.frameSize];
        this.e2 = new float[this.frameSize];

        System.out.println("INFO: AEC has been reset.");
        this.justRestart = true;
    }

    /**
     * AEC.
     *
     * @param farEndValue: the acoustic signal from far-end side (other
     * conversation participants).
     * @param microphoneValue: the acoustic signal from local microphone,
     * including local speech and echo.
     * @return : adjusted near-end signal without echo.
     */
    public short echoCancel(short farEndValue, short nearEndValue) {

        this.justRestart = false;

        /**
         * I. Output echo estimation of a frame size before.
         */
        //1. calculate echo for signal frame length before.
        float y_nom = this.calculateEcho(this.countInBuffer);

        this.Ys_T[this.countInBuffer] = y_nom; // save for mju updating.
        //2. calcuate output (e)
        //    d(n)-y(n)
        float d_nom = this.Ds_Frame_T[this.countInBuffer];
        float e_nom = d_nom - y_nom;

        this.Es_T[this.countInBuffer] = e_nom; // save for mju updating.

        //3. update weight
        this.updateWeights(e_nom, this.countInBuffer);


        /**
         * II. buffer latest input until there is a complete frame, and then
         * process the frame.
         */
        //new in v1.2: nomalisation of input
        float x_nom_buffer = farEndValue / SHORT_RANGE;
        float d_nom_buffer = nearEndValue / SHORT_RANGE;
        //new in v1.4: buffer x and d for an entire frame.
        this.Xs_Frame_Buffer_T[this.countInBuffer] = x_nom_buffer;
        this.Ds_Frame_Buffer_T[this.countInBuffer] = d_nom_buffer;

        this.countInBuffer++;
        //process a complete frame when we get enough.
        if (this.countInBuffer == this.frameSize / 2) {//over-lap save
            //update mju, after all relevant y and e have been calculated for x of an entire frame
            //keep initial value of learning rate for twice the filter length.
            if ((this.initialCount > 2 * (this.frameSize + this.tailLength))) {
                this.updateMju();
            }
            //apply AEC pre-process for an entire frame
            //far-end signal has been band-pass filted by far-end PC, so no need to process it again.
            //float[] x_frame_preprocessed = this.preFilter.bandPass(Xs_Frame_Buffer_T);
            float[] d_frame_preprocessed = this.filter.bandPass(Ds_Frame_Buffer_T);
            //copy pre-processed frame into the main array for echo calculation.
            for (int i = 0; i < this.frameSize / 2; i++) {//over-lap save
                this.Xs_T.add(Xs_Frame_Buffer_T[i]);
                this.Ds_Frame_T[i] = d_frame_preprocessed[i];
            }

            //over-lap save
            System.arraycopy(this.Ys_T, 0, this.Ys_T, this.frameSize / 2, this.frameSize / 2);
            System.arraycopy(this.Es_T, 0, this.Es_T, this.frameSize / 2, this.frameSize / 2);

            this.countInBuffer = 0;
        }

        /**
         * III. necessary statistics.
         */
        this.totalCount++;
        if (farEndValue != 0 && (this.initialCount <= 2 * (this.frameSize + this.tailLength))) {
            //this will not count for ever, in order to avoid overflow from INT range.
            this.initialCount++;
        }

        /**
         * IV. AEC Output.
         */
        //verify e_nom, value bigger than 1 or smaller than -1 could be lead by failed estimation or unprocessible signals (like background noise with an extremely high intensity)
        if (e_nom > 1 || e_nom < -1) {
            this.errorCount++;
            System.out.println("INFO:AEC Warning: Mic(e) Estimation Overflow![Count:"
                    + this.errorCount + "/" + this.restartThreshold + "/" + this.totalCount + ",value:" + e_nom + "]");
            e_nom = d_nom; // no echo cancellation

        }
        short e = clipFloat2Short(e_nom * SHORT_RANGE);

        if (this.errorCount >= this.restartThreshold) {
            // too many errors, and the AEC need to restart.
            this.reset();
            // return the original far-end signal without any process.
            e = farEndValue;
        }

        return e;
        // this method could reduce the volume of sound
        //return proprocessE(e);
    }

    /**
     * get the number of errors after the last reseting.
     *
     * @return the number of errors after the last reseting.
     */
    public int getErrorNumber() {
        return this.errorCount;
    }

    /**
     * E pro-process after main AEC procedure. but it is not used in this
     * version, because it reduce the volume of sound.
     *
     * @param e: microphone without echo.
     * @return : pro-processed AEC microphone output.
     */
    private short proprocessE(short e) {
        if (this.countInE_Buffer == this.frameSize / 2) {
            this.countInE_Buffer = 0;
            System.arraycopy(e2, 0, e2, this.frameSize / 2, this.frameSize / 2);
            System.arraycopy(e1, 0, e2, 0, this.frameSize / 2);
            e2 = this.filter.bandPass(e2);

        } else {
            e1[this.countInE_Buffer] = e;
            this.countInE_Buffer++;
        }

        return (short) e2[this.countInE_Buffer];
    }

    /**
     * check whether the restart just happen in the last signal process before.
     *
     * @return true if the restart just happen.
     */
    public boolean isJustRestart() {
        return this.justRestart;
    }

    /**
     * this echo estimation is for the far-end signal in the latest complete
     * frame, so there would be a delay of frame length for AEC process.
     *
     * @param indexInFrame: must be smaller than size of a Frame
     * @return: echo estimation
     */
    private float calculateEcho(int indexInFrame) {
        float echo = 0f;
        for (int k = 0; k < this.tailLength; k++) {
            //Wk(n)*x(n-k)
            echo += this.weights[k] * this.Xs_T.getElement(this.tailLength + indexInFrame - k);
        }

        return echo;
    }

    /**
     * update weights.
     *
     * @param e : near-end signal without echo.
     */
    private void updateWeights(float e, int indexInFrame) {
        //calcuate sum square of x
        float sum = this.Xs_T.getTailSumSquare(indexInFrame);

        // check sum: fix the problem when X queue is filled with all zeros..
        if (sum != 0) {
            /**
             * constant 0.5f is not in paper.
             *
             * Speex "when performing the weight update, we only move half the
             * way toward the "goal" this seems to reduce the effect of
             * quantization noise in the update phase. This can be seen as
             * applying a gradient descent on a "soft constraint" instead of
             * having a hard constraint."
             */
            for (int k = 0; k < this.tailLength; k++) {
                this.weights[k] = this.weights[k] + 0.5f * (this.mju * e * this.Xs_T.getElement(this.tailLength + indexInFrame - k) / sum);
            }
        }
    }

    /**
     * calculate learning rate (Mju).
     *
     * @return : learning rate.
     */
    private float getMju() {

        float[] mju_block0_T = this.fft.mjuIFFT(this.mjus_overlap_HalfComplex_F);
        float newMju = mju_block0_T[this.frameSize - 1];

        return newMju;
    }

    /**
     * update learning rate (Mju).
     */
    private void updateMju() {
        //0. FFT
        /* FFT will be applied on the first half of array, then complex result will be stored in the same array in following order:
         * a[2*k] = Re[k], 0<=k<filter length
         * a[2*k+1] = Im[k]
         */

        // Y
        System.arraycopy(this.Ys_HalfCompex_F, 0, this.Ys_pre_HalfCompex_F, 0, this.frameSize);
        //new in v 1.4; add hamming window before FFT
        this.Ys_HalfCompex_F = this.fft.yFFT(this.filter.hammingWindow(this.Ys_T));
        // E
        System.arraycopy(this.Es_HalfCompex_F, 0, this.Es_pre_HalfCompex_F, 0, this.frameSize);
        //new in v 1.4; add hamming window before FFT
        this.Es_HalfCompex_F = this.fft.eFFT(this.filter.hammingWindow(this.Es_T));
        //1. update Eit
        this.updateEit();
        //2. update Mju in frequency domain
        float mju_option;
        //mju[0]
        mju_option = this.eit
                * square(this.Ys_HalfCompex_F[0])
                / square(this.Es_HalfCompex_F[0]);
        if (mju_option < this.mjuMax_F) {
            this.mjus_overlap_HalfComplex_F[0] = mju_option;
        } else {
            this.mjus_overlap_HalfComplex_F[0] = this.mjuMax_F;
        }
        //mju[FFT_Nu/2]
        mju_option = this.eit
                * square(this.Ys_HalfCompex_F[1])
                / square(this.Es_HalfCompex_F[1]);
        if (mju_option < this.mjuMax_F) {
            this.mjus_overlap_HalfComplex_F[this.frameSize / 2] = mju_option;
        } else {
            this.mjus_overlap_HalfComplex_F[this.frameSize / 2] = this.mjuMax_F;
        }

        //mju[1:FFtNu/2-1]
        int real;
        int img;
        for (int k = 1; k < this.frameSize / 2; k++) {
            real = 2 * k;
            img = real + 1;
            mju_option = this.eit
                    * complex_square(this.Ys_HalfCompex_F[real], this.Ys_HalfCompex_F[img])
                    / complex_square(this.Es_HalfCompex_F[real], this.Es_HalfCompex_F[img]);
            this.mjus_overlap_HalfComplex_F[k] = (mju_option < this.mjuMax_F) ? mju_option : this.mjuMax_F;
        }

        //update Mju
        this.mju = this.getMju();

    }

    /**
     * update leakage estimation (Eit).
     */
    private void updateEit() {

        //0, get beta, which is independant of frequency k.
        float beta = this.beta0;
        float beta_option = this.totalPower_HalfComplex(this.Ys_HalfCompex_F)
                / this.totalPower_HalfComplex(this.Es_HalfCompex_F);

        if (beta_option < 1) {
            beta = this.beta0 * beta_option;
        }
        //System.out.println("Beta:"+beta);
        ///**
        if (beta > this.betaMax) {
            beta = this.betaMax;
        }
        // **/
        float beta_C = 1 - beta;
        //System.out.println("[" + this.frameCount + "]#beta:" + beta);

        int real;
        int img;
        //frequency[0]
        this.py_HalfComplex_F[0] = (1 - this.gama) * this.py_HalfComplex_F[0]
                + this.gama
                * (this.Ys_HalfCompex_F[0] * this.Ys_HalfCompex_F[0])
                - (this.Ys_pre_HalfCompex_F[0] * this.Ys_pre_HalfCompex_F[0]);
        this.pe_HalfComplex_F[0] = (1 - this.gama) * this.pe_HalfComplex_F[0]
                + this.gama
                * (this.Es_HalfCompex_F[0] * this.Es_HalfCompex_F[0])
                - (this.Es_pre_HalfCompex_F[0] * this.Es_pre_HalfCompex_F[0]);
        this.rey_HalfComplex_F[0] = beta_C * this.rey_HalfComplex_F[0]
                + beta * this.py_HalfComplex_F[0] * this.pe_HalfComplex_F[0];
        this.ryy_HalfComplex_F[0] = beta_C * this.ryy_HalfComplex_F[0]
                + beta * this.py_HalfComplex_F[0] * this.py_HalfComplex_F[0];
        //frequency[N/2]
        this.py_HalfComplex_F[this.frameSize / 2] = (1 - this.gama) * this.py_HalfComplex_F[this.frameSize / 2]
                + this.gama
                * (this.Ys_HalfCompex_F[1] * this.Ys_HalfCompex_F[1])
                - (this.Ys_pre_HalfCompex_F[1] * this.Ys_pre_HalfCompex_F[1]);
        this.pe_HalfComplex_F[this.frameSize / 2] = (1 - this.gama) * this.pe_HalfComplex_F[this.frameSize / 2]
                + this.gama
                * (this.Es_HalfCompex_F[1] * this.Es_HalfCompex_F[1])
                - (this.Es_pre_HalfCompex_F[1] * this.Es_pre_HalfCompex_F[1]);
        this.rey_HalfComplex_F[this.frameSize / 2] = beta_C * this.rey_HalfComplex_F[this.frameSize / 2]
                + beta * this.py_HalfComplex_F[this.frameSize / 2] * this.pe_HalfComplex_F[this.frameSize / 2];
        this.ryy_HalfComplex_F[this.frameSize / 2] = beta_C * this.ryy_HalfComplex_F[this.frameSize / 2]
                + beta * this.py_HalfComplex_F[this.frameSize / 2] * this.py_HalfComplex_F[this.frameSize / 2];

        //frequency[else]
        for (int k = 1; k < this.frameSize / 2; k++) {
            real = 2 * k;
            img = real + 1;
            this.py_HalfComplex_F[k] = (1 - this.gama) * this.py_HalfComplex_F[k]
                    + this.gama
                    * (complex_square(this.Ys_HalfCompex_F[real], this.Ys_HalfCompex_F[img])
                    - complex_square(this.Ys_pre_HalfCompex_F[real], this.Ys_pre_HalfCompex_F[img]));
            this.pe_HalfComplex_F[k] = (1 - this.gama) * this.pe_HalfComplex_F[k]
                    + this.gama
                    * (complex_square(this.Es_HalfCompex_F[real], this.Es_HalfCompex_F[img])
                    - complex_square(this.Es_pre_HalfCompex_F[real], this.Es_pre_HalfCompex_F[img]));
            this.rey_HalfComplex_F[k] = beta_C * this.rey_HalfComplex_F[k]
                    + beta * this.py_HalfComplex_F[k] * this.pe_HalfComplex_F[k];
            this.ryy_HalfComplex_F[k] = beta_C * this.ryy_HalfComplex_F[k]
                    + beta * this.py_HalfComplex_F[k] * this.py_HalfComplex_F[k];
        }

        /**
         * gain Eit and update global Eit.
         */
        float sum_Rey = this.rey_HalfComplex_F[0] + this.rey_HalfComplex_F[this.frameSize / 2];
        float sum_Ryy = this.ryy_HalfComplex_F[0] + this.ryy_HalfComplex_F[this.frameSize / 2];
        for (int k = 1; k < this.frameSize / 2; k++) {
            sum_Rey += 2 * this.rey_HalfComplex_F[k];
            sum_Ryy += 2 * this.ryy_HalfComplex_F[k];
        }
        this.eit = sum_Rey / sum_Ryy;
    }

    /**
     * calculate total power of a half complex array.
     *
     * @param p : half complex array.
     * @return : the total power.
     */
    private float totalPower_HalfComplex(float[] p) {
        float edgePower = square(p[0]) + square(p[1]);
        float symmetryPower = 0;
        float real;
        float img;
        for (int k = 1; k < this.frameSize / 2; k++) {
            real = p[2 * k];
            img = p[2 * k + 1];
            symmetryPower += complex_square(real, img);
        }

        return (edgePower + 2 * symmetryPower) / (this.frameSize);
    }

    /**
     * calculate the square of the absolute value of a complex number.
     *
     * @param real : the real part of the complex number.
     * @param image : the imaginary part of the complex number.
     * @return : the square of the absolute value.
     */
    public float complex_square(float real, float image) {
        return square(real) + square(image);
    }

    /**
     * calculate the power of a number. Math.pow() is not efficient in the case
     * of power 2, so this function is used.
     *
     * @param a : the number needed to be calculated.
     * @return : the power.
     */
    public float square(float a) {
        return a * a;
    }

    /**
     * Clip: solve the problem that range of Float is bigger than Short.
     *
     * @param a : value of Float which will be converted to Short.
     * @return : Short value of input variable.
     */
    private short clipFloat2Short(float a) {
        short result;
        if (a > Short.MAX_VALUE) {
            result = Short.MAX_VALUE;
        } else if (a < Short.MIN_VALUE) {
            result = Short.MIN_VALUE;
        } else {
            result = (short) a;
        }
        return result;
    }
}
