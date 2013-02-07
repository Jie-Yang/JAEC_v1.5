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
public class MainClassLiveProcess {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        //int frameMax = 600*1000;
        //("..\\sound_sources\\ding_16k_no_gap.wav");
        AudioManager speakerAM = new AudioManager("..\\sound_sources\\aec_micphone_16k.wav");
        AudioManager microphoneAM = new AudioManager("..\\sound_sources\\CRASH_16k.wav");
        
        //AudioManager speakerAM = new AudioManager("..\\sound_sources\\male2_16k_2m.wav");
        //AudioManager microphoneAM = new AudioManager("..\\sound_sources\\male1_16k_2m.wav");
        int frameMax = speakerAM.getFrames().size()*4;
        AudioManager aecAM = new AudioManager(microphoneAM.getAudioFormat());

        //standard reverberation time: 0.6s;
        //320 PCM samples every 20ms;
        //ideal filter length: 9600.
        //inter-time for AEC: 62.5 micros
        int echo_delay = 1900;//9600; 
        //create microphone input
        for (int n = 0; n < frameMax; n++) {
            short e = 0;
            if (n > echo_delay) {
                // reserve pure echo period for initilisation of AEC
                if (n < 300000) {
                    e = (short) (speakerAM.getFrame(n - echo_delay).getShort());
                }else if (n >= 300000 && n<=600000) {
                    e = (short) (speakerAM.getFrame(n - echo_delay).getShort()+microphoneAM.getFrame(n).getShort());
                }else{
                    e = (short) (speakerAM.getFrame(n - echo_delay).getShort());//+microphoneAM.getFrame(n).getShort());
                }
            }

            ShortAudioFrame outputFrame = new ShortAudioFrame(e, speakerAM.getAudioFormat().getFrameSize(), speakerAM.getAudioFormat().isBigEndian());
            aecAM.addFrame(outputFrame);
        }

        System.out.println("Microphone Input is Mixed.");
        //aecAM.playFrames(frameMax);

        int filter_length = echo_delay;
        JAEC fm = new JAEC(filter_length, microphoneAM.getAudioFormat().getSampleRate());

        //AEC on audio data and play the data.
        aecAM.setLivePlayer();
        System.out.println("[initial mju:"+fm.mju+"]");
        for (int n = 0; n < frameMax; n++) {

            long start = System.nanoTime();
            short e = fm.echoCancel(speakerAM.getFrame(n).getShort(), aecAM.getFrame(n).getShort());
            long end = System.nanoTime();
            ShortAudioFrame outputFrame = new ShortAudioFrame(e, microphoneAM.getAudioFormat().getFrameSize(), microphoneAM.getAudioFormat().isBigEndian());
            aecAM.playLiveFrames(outputFrame);

            //trigger AEC restart
            if(n==200000){
               //fm.errorCount = 51;
            }
            ///**
            if (n % 1000 == 0) {
                System.out.println(n / 1000 + "k[" + (end - start) / 1000 + " microS][mju:"+fm.mju+", eit:"+fm.eit+"]");
            }
            //*/
            //System.out.println(n+"[" + (end - start) / 1000 + " microS][mju:"+fm.mju+"]");
        }
    }
}