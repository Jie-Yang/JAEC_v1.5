
package testmain;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * this class helps to read sound files for testing.
 * @author Jie Yang
 */
public class AudioManager {

    private ArrayList audioFrameArray;
    private AudioFormat audioFormat;

    public AudioManager(AudioFormat audioFormat) {
        this.audioFormat = audioFormat;
        this.audioFrameArray = new ArrayList();
    }

    public AudioManager(String audioPath) {
        AudioInputStream audioStream = null;
        try {
            this.audioFrameArray = new ArrayList();
            File audioFile = new File(audioPath);
            AudioInputStream audioStream0 = AudioSystem.getAudioInputStream(audioFile);
            //default is PCM_SIGNED
            audioStream = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, audioStream0);
            this.audioFormat = audioStream.getFormat();
            System.out.println("Frame Nu. in File: " + audioStream.getFrameLength());
            System.out.println("Frame Size: " + this.audioFormat.getFrameSize() + " bytes");
            System.out.println("Frame Rate: " + this.audioFormat .getSampleRate() + " /s");
            System.out.println("PCM: " + this.audioFormat.getEncoding());
            boolean isBigEndian = this.audioFormat.isBigEndian();
            System.out.println("BigEndian : " + isBigEndian);
            //convert audio to byte array
            System.out.println("convert audio to byte array...");
            int byte_count = 0;
            byte[] frame = new byte[this.audioFormat.getFrameSize()];
            while (byte_count != -1) {
                byte_count = audioStream.read(frame, 0, frame.length);
                audioFrameArray.add(new ShortAudioFrame(frame, isBigEndian));
            }
            System.out.println("Frames Nu. Gained: " + audioFrameArray.size());
        } catch (UnsupportedAudioFileException ex) {
            Logger.getLogger(AudioManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(AudioManager.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                audioStream.close();
            } catch (IOException ex) {
                Logger.getLogger(AudioManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public ArrayList getFrames() {
        return this.audioFrameArray;
    }

    public ShortAudioFrame getFrame(int index) {

        index = index % this.audioFrameArray.size();
        return (ShortAudioFrame) this.audioFrameArray.get(index);
    }

    public void addFrame(ShortAudioFrame audioFrame) {
        this.audioFrameArray.add(audioFrame);
    }

    public AudioFormat getAudioFormat() {
        return this.audioFormat;
    }

    public void playFrames(int frameMax) {
        try {
            //connect with audio output port
            DataLine.Info audioInfo = new DataLine.Info(SourceDataLine.class, this.audioFormat);
            SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(audioInfo);

            audioLine.open(this.audioFormat);
            audioLine.start();
            //play audio bytes
            System.out.println("play audio bytes...");
            for (int i = 0; i < audioFrameArray.size() && i < frameMax; i++) {
                ShortAudioFrame tempFrame = (ShortAudioFrame) audioFrameArray.get(i);
                audioLine.write(tempFrame.getBytes(), 0, tempFrame.getBytes().length);

                /**
                if (i == 400000) {
                    System.out.println("########Near-end Speech#50000##");
                } else if (i == 90000) {
                    System.out.println("########Near-end Removed#90000##");
                    System.out.println("########Echo Path Changed#90000##");
                }
                 **/
            }
            System.out.println("play audio END");

            //close audio output port
            audioLine.drain();
            audioLine.close();
        } catch (LineUnavailableException ex) {
            Logger.getLogger(AudioManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private SourceDataLine audioLiveLine;

    public void setLivePlayer() {
        try {
            //connect with audio output port
            DataLine.Info audioInfo = new DataLine.Info(SourceDataLine.class, this.audioFormat);
            this.audioLiveLine = (SourceDataLine) AudioSystem.getLine(audioInfo);
            this.audioLiveLine.open(this.audioFormat);
            this.audioLiveLine.start();
            //play audio bytes
            System.out.println("Live Player is ready.");
        } catch (LineUnavailableException ex) {
            Logger.getLogger(AudioManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void playLiveFrames(ShortAudioFrame audioFrame) {
        this.audioLiveLine.write(audioFrame.getBytes(), 0, audioFrame.getBytes().length);
    }
}
