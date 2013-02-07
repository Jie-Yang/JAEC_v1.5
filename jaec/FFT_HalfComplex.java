package jaec;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

/**
 * this class is based on JTransforms Library 2.4.
 * Because real numbers are the input of FFT, only half complex output is necessary.
 * this can reduce the storage space and increase computing speed.
 * @author Jie Yang (Dublin, Januray, 2013)
 */
public class FFT_HalfComplex {

    // length of fft is determined by the speed of JTranforms.fft. 
    private int fftSize;// will cost 10 micro second for each fft
    private FloatFFT_1D fft;
    float[] yFFTSegment;
    float[] eFFTSegment;
    float[] jtransform_miu_t;

    /**
     * the constructor of this class.
     * @param fftSize : the size of FFT, which must be a power of 2.
     */
    public FFT_HalfComplex(int fftSize) {

        this.fftSize = fftSize;
        this.fft = new FloatFFT_1D(this.fftSize);
        this.yFFTSegment = new float[this.fftSize];
        this.eFFTSegment = new float[this.fftSize];
        this.jtransform_miu_t = new float[this.fftSize];
        //System.out.println("fftSize:" + this.fftSize);
    }

    /**
     * FFT on the latest near-end signals after AEC. 
     * only half complex frequency domain is gained in JTransfermation format below.
     * Re[0]=a[0];
     * Im[0]=0;
     * Re[k] = a[2*k], 0<k<n/2
     * Im[k] = a[2*k+1] 0<k<n/2
     * Re[n/2]=a[1];
     * Im[n/2]=0;
     * 
     * @param y : near-end signals after AEC.
     * @return  : the latest near-end signals after AEC in frequency domain. 
     */
    public float[] yFFT(float[] y) {

        //copy the new data.
        System.arraycopy(y, 0, this.yFFTSegment, 0, this.fftSize);
        //calcuate frequency domain of block 0
        this.fft.realForward(this.yFFTSegment);

        return this.yFFTSegment;
    }

    /**
     * FFT on the latest echo signals. 
     * only half complex frequency domain is gained in JTransfermation format below.
     * Re[0]=a[0];
     * Im[0]=0;
     * Re[k] = a[2*k], 0<k<n/2
     * Im[k] = a[2*k+1] 0<k<n/2
     * Re[n/2]=a[1];
     * Im[n/2]=0;
     * 
     * @param e : echo estimation signals.
     * @return  : the latest echo estimation signals in frequency domain. 
     */
    public float[] eFFT(float[] e) {
        //copy the new data.
        System.arraycopy(e, 0, this.eFFTSegment, 0, this.fftSize);
        //calcuate frequency domain of block 0
        this.fft.realForward(this.eFFTSegment);

        return this.eFFTSegment;
    }

    /**
     * Inverse FFT on learning rate in frequency domain.
     * 
     * @param mju_HalfComplex_f : learning rate in frequency domain with half complex.
     * @return : latest learning rate in time domain.
     */
    public float[] mjuIFFT(float[] mju_HalfComplex_f) {

        //rearrange order of frequency in JTransfermation format for half complex inverse FFT. 
        this.jtransform_miu_t[0] = mju_HalfComplex_f[0];
        this.jtransform_miu_t[1] = mju_HalfComplex_f[this.fftSize / 2];
        for (int i = 2; i < this.fftSize / 2; i = i + 2) {
            this.jtransform_miu_t[i] = mju_HalfComplex_f[i];
            this.jtransform_miu_t[i + 1] = 0;
        }
        this.fft.realInverse(this.jtransform_miu_t, true);

        return this.jtransform_miu_t;
    }
}
