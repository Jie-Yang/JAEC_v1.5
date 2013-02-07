/*
 * 
 * @author Jie Yang (Dublin, Januray, 2013)
 * 
 * Hamming Window
 * this implemenation is based on algorithm on Wikipedia: 
 * http://en.wikipedia.org/wiki/Window_function, accessed at 24th Jan 2013.
 * 
 * Band Pass Filter
 */
package jaec;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

public class JAEC_Filter {

    private int lowerIndex;
    private int higherIndex;
    private int frame_size;
    private float[] hammingWeights;
    private float sampleRate;

    /**
     * Constructor of this class.
     *
     * @param lowerHZ: lower boundary in Hz for band-pass filter.
     * @param higherHZ: higher boundary in Hz for band-pass filter.
     * @param sample_rate: sample rate of input stream.
     * @param frame_size : frame size of input steam (also the same as fft
     * size).
     */
    public JAEC_Filter(int lowerHZ, int higherHZ, float sample_rate, int frame_size) {
        this.sampleRate = sample_rate;
        this.frame_size = frame_size;
        this.lowerIndex = (int) (this.frame_size * lowerHZ / this.sampleRate);
        this.higherIndex = (int) (this.frame_size * higherHZ / this.sampleRate);

        //initialisation of Hamming 
        this.hammingWeights = new float[this.frame_size];
        float a = 0.54f;
        float b = 1 - a;
        for (int i = 0; i < this.frame_size; i++) {
            hammingWeights[i] = (float) (a - b * (Math.cos(2 * Math.PI * i / (this.frame_size - 1))));
        }
    }

    /**
     * hamming window function.
     *
     * @param x: array of signal for hamming window process.
     * @return : hamming window result.
     */
    public float[] hammingWindow(float[] x) {
        float[] y = new float[this.frame_size];
        for (int i = 0; i < this.frame_size; i++) {
            y[i] = hammingWeights[i] * x[i];
        }
        return y;
    }

    /**
     * band pass filter.
     *
     * @param x: array of signal for band pass filtering.
     * @return : band-pass filter result.
     */
    public float[] bandPass(float[] x) {
        float[] y = new float[this.frame_size * 2];
        System.arraycopy(x, 0, y, 0, frame_size);

        FloatFFT_1D fft = new FloatFFT_1D(this.frame_size);
        fft.realForwardFull(y);

        //remove low frequency
        for (int i = 0; i <= this.lowerIndex; i++) {
            y[2 * i] = 0;
            y[2 * i + 1] = 0;
        }
        //remove high frequency
        for (int i = this.higherIndex; i < this.frame_size; i++) {
            y[2 * i] = 0;
            y[2 * i + 1] = 0;
        }
        fft.complexInverse(y, true);

        float[] y1 = new float[this.frame_size];
        for (int i = 0; i < this.frame_size; i++) {
            y1[i] = y[2 * i];
        }

        return y1;
    }
}
