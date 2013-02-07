/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package testmain;

import jaec.JAEC;

/**
 *
 * @author Jie Yang
 */
public class MainClassArtificialSound {
    
    public static void main(String[] args) {

        //int frameMax = 600*1000;
        //("..\\sound_sources\\ding_16k_no_gap.wav");
        AudioManager templateAM = new AudioManager("..\\sound_sources\\ding_16k_8s.wav");
        //AudioManager speechAM = new AudioManager("..\\sound_sources\\aec_micphone_16k.wav");
        int frameMax = 300*1000;

        
        AudioManager speakerAM = new AudioManager(templateAM.getAudioFormat());
        AudioManager microphoneAM = new AudioManager(templateAM.getAudioFormat());

        int echo_delay = 3000;
        //create microphone input
        for (int n = 0; n < frameMax; n++) {
            
            short e=10;
            ShortAudioFrame outputFrame = new ShortAudioFrame(e, speakerAM.getAudioFormat().getFrameSize(), speakerAM.getAudioFormat().isBigEndian());
            speakerAM.addFrame(outputFrame);
            e=10;
            outputFrame = new ShortAudioFrame(e, speakerAM.getAudioFormat().getFrameSize(), speakerAM.getAudioFormat().isBigEndian());
            microphoneAM.addFrame(outputFrame);
        }
        //microphone = microphoneAM.getFrames();
        System.out.println("Microphone Input is Mixed.");
        //aecAM.playFrames(frameMax);

        /*
         * plus 10, because weight length must be bigger than maximal echo-delay.
         */
        int filter_length = echo_delay + 10;
        JAEC fm = new JAEC(filter_length, templateAM.getAudioFormat().getSampleRate());

        //AEC on audio data and play the data.
        microphoneAM.setLivePlayer();
        for (int n = 0; n < frameMax; n++) {

            long start = System.nanoTime();
            short e = fm.echoCancel(speakerAM.getFrame(n).getShort(), microphoneAM.getFrame(n).getShort());
            long end = System.nanoTime();
            ShortAudioFrame outputFrame = new ShortAudioFrame(e, templateAM.getAudioFormat().getFrameSize(), templateAM.getAudioFormat().isBigEndian());
            microphoneAM.playLiveFrames(outputFrame);

            if (n % 1000 == 0) {
                System.out.println(n / 1000 + "k[" + (end - start) / 1000 + " microS]");
            }
        }
    }
    
}
