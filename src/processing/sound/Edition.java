package processing.sound;

import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import java.io.*;
import processing.CommonMethods;

import javax.sound.sampled.*;

class Edition {
    private static File file;
    private static double[] soundArr;

    void setFile() {
        file = soundFile();
        if (file != null) {
            try (FileInputStream fileInputStream = new FileInputStream(file)){
                soundArray(fileInputStream);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    BarChart<String, Number> getChart(double numberOfPeriods) {
        double period = numberOfPeriods / frequency();
        return linearGraphData(period);
    }

    BarChart<String, Number> getFourierChart() {
        double[] wnd = rectangularFunc(1);
        double[][] amplitudeAndFrequencies = stft(1, wnd);
        return fourierChart(amplitudeAndFrequencies[0], amplitudeAndFrequencies[1]);
    }

    void doSTFT(String functionType, int widowLength,int hopSizeShift){
        frequency();
        double[] wnd;
        switch (functionType) {
            case "rectangular":
                wnd = rectangularFunc(widowLength);
                break;
            case "vonHann":
                wnd = hannFunc(widowLength);
                break;
            case "hamming":
                wnd = hammingFunc(widowLength);
                break;
            case "lowPassFilter":
                wnd = lowPassFilter(widowLength);
                break;
            default:
                throw new Error("No such operation");
        }
        stft(hopSizeShift, wnd);
    }

    void doInverseFourier() {
        double[] wnd = rectangularFunc(1);
        iSTFT(wnd);
    }

    void playActualTrack() {
        playSound(soundArr);
    }

    void playInputSound() {
        try {
            playInSound();
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    void playMainFrequency() {
        double[] alternatingFrequency = wholeFrequencies();
        playSound(alternatingFrequency);
    }


    private static File soundFile() {
        File file = new CommonMethods().file("wav", "*.wav");
        System.out.println(file);
        return file;
    }


    private void playInSound() throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        AudioInputStream stream = AudioSystem.getAudioInputStream(file);
        AudioFormat format = stream.getFormat();
        DataLine.Info info = new DataLine.Info(Clip.class, format);
        Clip clip = (Clip) AudioSystem.getLine(info);
        sound(stream, clip);
    }

    private static void sound(AudioInputStream stream, Clip clip) {
        try{
            clip.open(stream);
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            clip.close();
        }
    }

    private static long trackLength() {
        try {
            AudioInputStream audioInputStream;
            audioInputStream = AudioSystem.getAudioInputStream(file);
            return audioInputStream.getFrameLength();

        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static void soundArray(FileInputStream fileInputStream) throws IOException {
        long frames = trackLength();
        byte[] buf = new byte[(int) frames * 2];
        soundArr = new double[buf.length / 2];
        int readBuf = fileInputStream.read(buf);
        if (readBuf > 0) {
            for (int i = 0; i < buf.length / 2; ++i) {
                soundArr[i] = (double) ((short) (buf[i * 2] & 255 | buf[i * 2 + 1] << 8));
            }
        } else throw new IllegalArgumentException();
        fileInputStream.close();
    }

    private double average() {
        int plus = 0;
        double positiveValues = 0;
        for (int i = 0; i < soundArr.length - 1; i++) {
            if (soundArr[i] > 0) {
                positiveValues = soundArr[i] + positiveValues;
                plus++;
            }
        }
        return positiveValues / plus;
    }

    double[] RemoveNoise() {
        double[] cleanSound = new double[soundArr.length];
        for (int i = 19; i < soundArr.length - 19; ++i) {
            for (int j = -9; j <= 9; ++j) {
                if (soundArr[i + j] > average() * 3.0D) {
                    soundArr[i + j] = 0.0D;
                }
                if (soundArr[i - 1] == 0.0D && soundArr[i + 1] == 0.0D) {
                    soundArr[i] = 0.0D;
                }
                cleanSound[i] += soundArr[i + j];
            }
            cleanSound[i] /= 10.0D;
        }
        return cleanSound;
    }

    private int ZeroPlacesNumber() {
        double[] track = RemoveNoise();
        int zeroPlaces = 0;
        for (int i = 0; i < track.length - 1; ++i) {
            if (track[i] < 0.0D && track[i + 1] < 0.0D && track[i + 2] > 0.0D && track[i + 3] > 0.0D) {
                ++zeroPlaces;
            }
        }
        return zeroPlaces;
    }

    private static double soundTime() {
        AudioInputStream audioInputStream;
        double durationInSeconds = 0;
        try {
            audioInputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioInputStream.getFormat();
            long frames = audioInputStream.getFrameLength();
            durationInSeconds = ((double) frames + 0.0D) / (double) format.getFrameRate();
        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
        return durationInSeconds;
    }

    private double frequency() {
        return round(ZeroPlacesNumber() / soundTime());
    }

    private static double round(double value) {
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(0);
        return value;
    }

    double[] wholeFrequencies() {
        int firstZeroPlaces = 0;
        double previousFrame = 0.0D;
        double timePeriod = 0.0D;
        int zeroPlaces = ZeroPlacesNumber();
        double[] track = RemoveNoise();
        double[] alternatingFrequency = new double[zeroPlaces + 1];
        long frames;
        double durationInSeconds;
        try {
            AudioInputStream audioInputStream;
            audioInputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioInputStream.getFormat();

            for (int i = 0; i < track.length - 3; ++i) {
                if (track[i] < 0.0D && track[i + 1] < 0.0D && track[i + 2] > 0.0D && track[i + 3] > 0.0D) {
                    frames = (long) ((double) i - previousFrame);
                    durationInSeconds = ((double) frames + 0.0D) / (double) format.getFrameRate();
                    timePeriod += durationInSeconds;
                    alternatingFrequency[firstZeroPlaces] = (double) ((int) (1.0D / durationInSeconds));
                    System.out.println(firstZeroPlaces + 1 + ". Frequency: " + alternatingFrequency[firstZeroPlaces] + "Hz, Moment: " + timePeriod + ", Duration: " + durationInSeconds);
                    ++firstZeroPlaces;
                    previousFrame = (double) i;
                }
            }
        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
        System.out.print(Arrays.toString(alternatingFrequency));
        return alternatingFrequency;
    }


    void groupFrequencies() {
        double[] alternatingFrequency = wholeFrequencies();
        int[] array = new int[alternatingFrequency.length];
        Map<Integer, Integer> hm = new HashMap<>(); ///??
        for (int i = 0; i < alternatingFrequency.length; ++i) {
            array[i] = (int) alternatingFrequency[i];
        }

        int highestVote = array.length;
        for (int var4 = 0; var4 < highestVote; ++var4) {
            int x = array[var4];
            if (!hm.containsKey(x)) {
                hm.put(x, 1);
            } else {
                hm.put(x, hm.get(x) + 1);
            }
        }

        System.out.println("Basic frequencies: ");
        System.out.println(hm);
        Integer highestMap = 0;

        for (Entry<Integer, Integer> integerIntegerEntry : hm.entrySet()) {
            if (integerIntegerEntry.getValue() > highestVote) {
                highestMap = integerIntegerEntry.getKey();
                highestVote = integerIntegerEntry.getValue();
            }
        }
        System.out.println("Most frequent frequency: " + highestMap + "Hz, Times occur: " + highestVote);
    }

    private BarChart<String, Number> getChart(Series series, String titleSet, String xLabel, String yLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> fc = new BarChart<>(xAxis, yAxis);
        fc.setTitle(titleSet);
        xAxis.setLabel(xLabel);
        yAxis.setLabel(yLabel);
        fc.getData().clear();
        fc.layout();
        fc.getData().addAll(series);
        return fc;
    }

    private BarChart<String, Number> linearGraphData(double period) {
        Series<String, Number> series = new Series<>();
        double[] track = RemoveNoise();
        long trackLen = trackLength();
        for (int i = 0; i < trackLen * period; ++i) {
            series.getData().add(new Data<>(Integer.toString(i), track[i]));
        }
        return getChart(series, "Sound signal in time domain", "Time", "Volume");
    }

    private BarChart<String, Number> fourierChart(double[] trackAmplitude, double[] frequency) {
        double f = frequency();
        Series<String, Number> series = new Series<>();
        for (int i = 0; (double) i < (double) frequency.length * (f / (double) (2 * frequency.length)) - 1.0D; ++i) {
            series.getData().add(new Data<>(Double.toString((double) ((int) frequency[i + 1])), trackAmplitude[i]));
        }
        return getChart(series, "Fourier transform of processing.sound signal in time domain", "Frequency", "Amplitude");
    }

    private double[] rectangularFunc(int windowMaskSize) {
        double[] wnd = new double[windowMaskSize];
        Arrays.fill(wnd, 1);
        return wnd;
    }

    private double[] hannFunc(int windowMaskSize) {
        double[] wnd = new double[windowMaskSize];
        for (int i = 0; i < wnd.length; ++i) {
            wnd[i] = 0.5D * (1.0D - Math.cos(2 * Math.PI * i / (wnd.length - 1.0D))) * 2.0D;
        }
        return wnd;
    }

    private double[] hammingFunc(int windowMaskSize) {
        double[] wnd = new double[windowMaskSize];
        for (int i = 0; i < wnd.length; ++i) {
            wnd[i] = 0.5D - 0.46D * Math.cos(2 * Math.PI * i / (wnd.length - 1.0D));
        }
        return wnd;
    }

    private double[] lowPassFilter(int windowMaskSize) {
        double[] wnd = rectangularFunc(windowMaskSize);
        double[] lowPassFilterArr = new double[wnd.length];
        double samplingFrequency = 44100.0D;
        int wndLen = wnd.length;

        for (int k = 0; k < wndLen; ++k) {
            if (k % 2 == 0) {
                lowPassFilterArr[k] = 2.0D * windowMaskSize / samplingFrequency;
            } else {
                lowPassFilterArr[k] = Math.sin(2 * Math.PI * windowMaskSize / samplingFrequency * (double) (k - (wndLen - 1) / 2)) / (Math.PI * (double) (k - (wndLen - 1) / 2));
            }
            wnd[k] *= lowPassFilterArr[k];
        }
        return wnd;
    }


    private double[][] stft(int windowShift, double[] wnd) {
        int N = soundArr.length / 100;
        double[] inReal = soundArr;
        double[] outReal = new double[soundArr.length];
        double[] outImag = new double[soundArr.length];
        double[][] amplitudeAndFrequencies = new double[2][];
        amplitudeAndFrequencies[0] = new double[soundArr.length];
        amplitudeAndFrequencies[1] = new double[N / 2];

        System.out.println("Frequency of samples: " + N);

        for (int k = 0; k < N; ++k) {
            double sumReal = 0.0D;
            double sumImag = 0.0D;
            for (int n = 0; n < N; ++n) {
                for (int m = 0; m < wnd.length; ++m) {
                    if (n - m + windowShift < 0 || n - m + windowShift >= N) {
                        sumReal += 0.0D;
                        sumImag += 0.0D;
                    } else {
                        double angle = -2 * Math.PI * (double) n * (double) k / (double) N;
                        sumReal += inReal[n - m + windowShift] * wnd[m] * Math.cos(angle);
                        sumImag += inReal[n - m + windowShift] * wnd[m] * Math.sin(angle);
                    }
                }
            }
            outReal[k] = sumReal * 2.0D / (double) N;
            outImag[k] = sumImag * 2.0D / (double) N;

            if (k < N / 2) {
                amplitudeAndFrequencies[1][k] = (double) (N * k);
            }
        }
        for (int i = 0; i < outReal.length; ++i) {
            amplitudeAndFrequencies[0][i] = Math.sqrt(outReal[i] * outReal[i] + outImag[i] * outImag[i]);
        }

        return amplitudeAndFrequencies;
    }


    private void iSTFT(double[] wnd) {
        double[] frequency = stft(1, wnd)[0];
        int N = frequency.length;
        double[] inImag = new double[N];
        double[] outReal = new double[N];
        Arrays.fill(inImag, 1);
        int samplesFrequency = 50;
        System.out.println("Frequency of samples: " + samplesFrequency);
        for (int n = 0; n < N; ++n) {
            double sumReal = 0.0D;
            for (double v : wnd) {
                for (int k = 0; k < samplesFrequency; ++k) {
                    double angle = 2 * Math.PI * (double) k * (double) n / (double) N;
                    sumReal += frequency[k] * Math.sin(angle) + inImag[k] * Math.cos(angle);
                }
                outReal[n] = 100.0D * sumReal / ((double) N * v);
            }
        }
        System.out.println("iSTFT: " + Arrays.toString(outReal));
    }


    private static void playTon(double durationMs, int numberOfTimesFullSinFuncPerSec) throws LineUnavailableException {
        int frequency = 0;
        byte[] buf = new byte[2];
        AudioFormat af = new AudioFormat((float) frequency, 16, 1, true, false);
        SourceDataLine sdl = AudioSystem.getSourceDataLine(af);

        sdl.open();
        sdl.start();
        for (int i = 0; (double) i < durationMs; ++i) {
            float numberOfSamplesToRepresentFullSin = (float) frequency / (float) numberOfTimesFullSinFuncPerSec;
            double angle = (double) i / ((double) numberOfSamplesToRepresentFullSin / 2.0D) * Math.PI;
            short a = (short) ((int) (Math.sin(angle) * 32767.0D));
            buf[0] = (byte) (a & 255);
            buf[1] = (byte) (a >> 8);
            sdl.write(buf, 0, 2);
        }
        sdl.drain();
        sdl.stop();
    }

    private static void playSound(double[] sound) {
        double durationInSeconds = soundTime();
        for (int z = 0; z < sound.length / 2; ++z) {
            double durationMs = durationInSeconds * 1000.0D / (double) sound.length * 44100.0D;
            int numberOfTimesFullSinFuncPerSec = (int) sound[z];
            try {
                playTon(durationMs, numberOfTimesFullSinFuncPerSec);
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        }
    }

}
