/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package testmain;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

/**
 *
 * @author Jie Yang
 */
public class Test {

    public static void main(String[] args) {
        
        int a = (int) (512 * 50 / 16000f);
        
        System.out.println(a);

    }
    
    public static void Num2SoundTest(){
        
        
        AudioManager speakerAM = new AudioManager("..\\sound_sources\\male1_16k_2m.wav");

        AudioManager aecAM = new AudioManager(speakerAM.getAudioFormat());


        //create microphone input
        int frameMax = 160 * 1000;
        
        /**
         * Scenario I. pure echo cancellation.
        */
        short e = -15828;
        for (int n = 0; n < frameMax; n++) {
            //short [-32,768; 32,767]
            ShortAudioFrame outputFrame = new ShortAudioFrame(e, speakerAM.getAudioFormat().getFrameSize(), speakerAM.getAudioFormat().isBigEndian());
            aecAM.addFrame(outputFrame);
        }


        System.out.println("Microphone Input is Mixed.");
        aecAM.playFrames(frameMax);
    }

    public static void FFTTest() {
        double[] a = new double[16];

        for (int i = 0; i < 16; i++) {
            a[i] = i + 1;
        }

        DoubleFFT_1D fft = new DoubleFFT_1D(8);
        fft.realForward(a);
        a[16/2] = a[1];
        a[1]=0;
        a[16/2+1]=0;

        for (int i = 0; i < 8; i++) {
            System.out.print(a[2 * i] + "#" + a[2 * i + 1] + "\n");
        }
        System.out.println("[inverseFFT]");

        fft.complexInverse(a, true);

        for (int i = 0; i < 8; i++) {
            System.out.println(a[2 * i]);
        }
    }
    
    public static void IFFTTest() {
        double[] a = new double[8];

        for (int i = 0; i < 8; i++) {
            a[i] = i;
        }
        
        double[] c = a;
        
        double[] b = new double[8];

        for (int i = 0; i < 8; i++) {
            b[i] = i+10;
        }


        a = b;
        System.out.println(c[1]);

    }


    public static void FFTTest4() {
        double[] a = new double[4];
        a[0] = 3;
        a[1] = 5;



        DoubleFFT_1D fft = new DoubleFFT_1D(2);
        fft.realForwardFull(a);

        for (int i = 0; i < 2; i++) {
            System.out.print(a[2 * i] + "#" + a[2 * i + 1] + "\n");
        }

    }

    public static void FFTTest2() {
        double[] a = new double[180];

        for (int i = 0; i < 90; i++) {
            a[i] = 1;
        }

        DoubleFFT_1D fft = new DoubleFFT_1D(90);
        fft.realInverseFull(a, true);

        for (int i = 0; i < 180; i++) {
            System.out.print(i + ":" + a[i] + "\n");
        }

    }

    public static void FFTTest3() {
        double[] a = new double[16];

        for (int i = 0; i < 16; i++) {
            a[i] = i + 1;
        }

        DoubleFFT_1D fft = new DoubleFFT_1D(8);
        fft.realForwardFull(a);

        for (int i = 0; i < 8; i++) {
            System.out.print(a[2 * i] + "#" + a[2 * i + 1] + "\n");
        }
        System.out.println("[inverseFFT]");

        fft.complexInverse(a, true);

        for (int i = 0; i < 8; i++) {
            System.out.println(a[2 * i]);
        }
    }

    /**
     *            FFT                  Inverse
     * JTransfer: [16553 microS]     [17196 microS]
     */
    public static void JtransformsPerformance() {
        int filter_length = 32;//2^14
        double[] a = new double[2 * filter_length];

        for (int i = 0; i < filter_length; i++) {
            a[i] = 1000 * i + 1;
        }

        DoubleFFT_1D fft = new DoubleFFT_1D(filter_length);


        //FFT/////////////////////////////////////////////////////////////////
        for (int i = 0; i < 200; i++) {
            long start0 = System.nanoTime();
            fft.realForwardFull(a);
            //End of FFT
            long end0 = System.nanoTime();
            System.out.println(i + "-FFT[" + (end0 - start0) / 1000 + " microS]");
        }

        //End of FFT

        //////////////////////////////////////////////////////////////////////
        for (int i = 0; i < 200; i++) {
            long start0 = System.nanoTime();
            fft.realInverse(a, true);
            //End of FFT
            long end0 = System.nanoTime();
            System.out.println(i + "-IFFT[" + (end0 - start0) / 1000 + " microS]");
        }

    }

    public static void JtransformsPerformanceFloat() {
        int filter_length = 64;//2^14[16384]
        float[] a = new float[2 * filter_length];

        for (int i = 0; i < filter_length; i++) {
            a[i] = 1000 * i + 1;
        }
        float[] b = a.clone();

        FloatFFT_1D fft = new FloatFFT_1D(filter_length);

        fft.realForwardFull(b);
        fft.realForwardFull(b);

        long start0 = System.nanoTime();

        //FFT/////////////////////////////////////////////////////////////////

        fft.realForwardFull(a);
        //End of FFT
        long end0 = System.nanoTime();
        System.out.println("FFT[" + (end0 - start0) / 1000 + " microS]");
        //////////////////////////////////////////////////////////////////////
        long start1 = System.nanoTime();
        //Inverse FFT
        fft.realInverse(a, true);
        //End of Inverse FFT


        long end1 = System.nanoTime();
        System.out.println("IFFT[" + (end1 - start1) / 1000 + " microS]");

    }


    public static short calculateEchoTime() {
        int s = 2 * 9600;
        double[] a = new double[s];
        double[] b = new double[s];
        short echo = 0;

        long start0=0;
        long end0=0;
        for (int i = 0; i < 200; i++) {
            start0 = System.nanoTime();
            for (int k = 0; k < s; k++) {
                echo += a[k] * b[s - k - 1]; //Wk(n)*x(n-k)
            }
            //End of FFT
            end0 = System.nanoTime();
            
        }
        System.out.println("FFT[" + (end0 - start0) / 1000 + " microS]");

        return echo;
    }
    
    public static short calculateEchoTime2() {
        int s = 2*9600;
        float[] a = new float[s];
        float[] b = new float[s];
        short echo = 0;

        long start0=0;
        long end0=0;
        for (int i = 0; i < 200; i++) {
            start0 = System.nanoTime();
            
            for (int k = 0; k < s; k++) {
                echo += a[k] * b[s - k - 1]; //Wk(n)*x(n-k)
            }
            
            //End of FFT
            end0 = System.nanoTime();
            
        }
        System.out.println("FFT[" + (end0 - start0) / 1000 + " microS]");

        return echo;
    }


    public static void arraycopyTest(){
        int s = 3200;
        float[] a = new float[s];
        float[] b = new float[s];
        short echo = 0;

        long start0=0;
        long end0=0;
        for (int i = 0; i < 200; i++) {
            start0 = System.nanoTime();
            
            System.arraycopy(a, 0, b, 0, s);
            
            //End of FFT
            end0 = System.nanoTime();
            
        }
        System.out.println("FFT[" + (end0 - start0) / 1000 + " microS]");
    }
    

}
