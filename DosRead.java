import java.io.*;

public class DosRead {
  static final int FP = 1000;
  static final int BAUDS = 100;
  static final int[] START_SEQ = { 1, 0, 1, 0, 1, 0, 1, 0 };
  FileInputStream fileInputStream;
  int sampleRate = 44100;
  int bitsPerSample;
  int dataSize;
  double[] audio;
  int[] outputBits;
  char[] decodedChars;

  /**
   * Constructor that opens the FIlEInputStream
   * and reads sampleRate, bitsPerSample and dataSize
   * from the header of the wav file
   *
   * @param path the path of the wav file to read
   */
  public void readWavHeader(String path) {
    byte[] header = new byte[44]; // The header is 44 bytes long
    try {
      fileInputStream = new FileInputStream(path);
      fileInputStream.read(header);

      // Récupération des informations du header
      sampleRate = byteArrayToInt(header, 24, 32);
      bitsPerSample = byteArrayToInt(header, 34, 16);
      dataSize = byteArrayToInt(header, 40, 32);

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Helper method to convert a little-endian byte array to an integer
   *
   * @param bytes  the byte array to convert
   * @param offset the offset in the byte array
   * @param fmt    the format of the integer (16 or 32 bits)
   * @return the integer value
   */
  private static int byteArrayToInt(byte[] bytes, int offset, int fmt) {
    if (fmt == 16)
      return ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
    else if (fmt == 32)
      return ((bytes[offset + 3] & 0xFF) << 24) | ((bytes[offset + 2] & 0xFF) << 16) | ((bytes[offset + 1] & 0xFF) << 8)
          | (bytes[offset] & 0xFF);
    else
      return (bytes[offset] & 0xFF);
  }

  /**
   * Read the audio data from the wav file
   * and convert it to an array of doubles
   * that becomes the audio attribute
   */
  public void readAudioDouble() {
    byte[] audioData = new byte[dataSize];

    try {
        fileInputStream.read(audioData);
    } catch (IOException e) {
        e.printStackTrace();
    }

    // Conversion des données audio en tableau de doubles
    audio = new double[dataSize / 2];
    bitsPerSample = 16; // Nombre de bits par échantillon (à adapter si nécessaire)

    for (int i = 0; i < dataSize / 2; i++) {
        // Conversion little-endian des octets en entier
        int sample = (audioData[2 * i + 1] << 8) | (audioData[2 * i] & 0xFF);

        // Normalisation entre -1 et 1
        audio[i] = sample / 32768.0;
    }
}


  /**
   * Reverse the negative values of the audio array
   */
  public void audioRectifier() {
    for (int i = 0; i < audio.length; i++) {
        if (audio[i] < 0) {
            audio[i] = -audio[i];
        }
    }
}

  /**
   * Apply a low pass filter to the audio array
   * Fc = (1/2n)*FECH
   *
   * @param n the number of samples to average
   */
  public void audioLPFilter(int n) {
    double[] filteredAudio = new double[audio.length];

    // Apply low pass filter (moving average)
    for (int i = 0; i < audio.length; i++) {
      double sum = 0.0;
      int start = Math.max(0, i - n + 1);
      int end = i + 1;

      for (int j = start; j < end; j++) {
        sum += audio[j];
      }

      filteredAudio[i] = sum / (double) (end - start);
    }

    // Update the audio array with the filtered values
    audio = filteredAudio;
  }

  /**
   * Resample the audio array and apply a threshold
   *
   * @param period    the number of audio samples by symbol
   * @param threshold the threshold that separates 0 and 1
   */
  public void audioResampleAndThreshold(int period, int threshold) {
    int numSymbols = audio.length / period;
    outputBits = new int[numSymbols];

    for (int i = 0; i < numSymbols; i++) {
        int start = i * period;
        int end = start + period;

        // Calculer la somme des échantillons dans la période
        double sum = 0;
        for (int j = start; j < end; j++) {
            sum += audio[j];
        }

        int MAX_AMP = (int) Math.pow(2, bitsPerSample);

        // Calculer la moyenne en prenant en compte la plage d'amplitude
        double average = MAX_AMP * (sum / period);

        // Appliquer le seuillage
        outputBits[i] = (average > threshold) ? 1 : 0;
    }
    // Imprimer le tableau outputBits dans le terminal
System.out.print("outputBits: ");
for (int i = 0; i < outputBits.length; i++) {
    System.out.print(outputBits[i] + " ");
}
System.out.println();  // Nouvelle ligne à la fin pour la lisibilité
}


  /**
   * Decode the outputBits array to a char array
   * The decoding is done by comparing the START_SEQ with the actual beginning of
   * outputBits.
   * The next first symbol is the first bit of the first char.
   */
  public void decodeBitsToChar() {
    // Trouver l'index de la séquence de début dans le tableau outputBits
    int startSeqIndex = -1;

    for (int i = 0; i < outputBits.length - START_SEQ.length; i++) {
        boolean match = true;
        for (int j = 0; j < START_SEQ.length; j++) {
            if (outputBits[i + j] != START_SEQ[j]) {
                match = false;
                break;
            }
        }
        if (match) {
            startSeqIndex = i + START_SEQ.length;
            break;
        }
    }

    if (startSeqIndex == -1) {
        System.out.println("Start sequence not found in the message.");
        return;
    }

    // Calculer le nombre de symboles dans le message
    int numMessageSymbols = (outputBits.length - startSeqIndex) / 8; // Chaque caractère utilise 8 bits

    // Initialiser le tableau decodedChars
    decodedChars = new char[numMessageSymbols];

    // Décoder les bits en utilisant la stratégie de rééchantillonnage
    for (int i = 0; i < numMessageSymbols; i++) {
        int start = startSeqIndex + i * 8;
        int end = start + 8; // Utiliser 8 bits pour chaque caractère

        // Convertir les 8 bits en un entier (traiter les bits dans l'ordre inverse)
        int decodedValue = 0;
        for (int j = end - 1; j >= start; j--) {
            decodedValue = (decodedValue << 1) | outputBits[j];
        }

        // Convertir l'entier en caractère ASCII
        decodedChars[i] = (char) decodedValue;
    }

    // Afficher les caractères décodés pour inspection
    System.out.print("Caractères décodés: ");
    for (char c : decodedChars) {
        System.out.print(c);
    }
    System.out.println();  // Nouvelle ligne à la fin pour la lisibilité
}



  /**
   * Print the elements of an array
   *
   * @param data the array to print
   */
  public static void printIntArray(char[] data) {
    for (char c : data) {
      System.out.print(c);
    }
    System.out.println();
  }

  /**
   * Display a signal in a window
   *
   * @param sig   the signal to display
   * @param start the first sample to display
   * @param stop  the last sample to display
   * @param mode  "line" or "point"
   * @param title the title of the window
   */
  public static void displaySig(double[] sig, int start, int stop, String mode, String title) {
    StdDraw.setCanvasSize(1200, 900);
    StdDraw.setXscale(start, stop);
    StdDraw.setYscale(-1.1, 1.1);
    StdDraw.setTitle(title);

    // Affichage de l'échelle de graduation sur l'axe x
    for (int i = start; i <= stop; i += (stop - start) / 10) {
        StdDraw.text(i, -1.0, String.valueOf(i));
        StdDraw.line(i, -0.02, i, 0.02);
    }

    // Ajout de la ligne bleue au milieu de la fenêtre graphique
    StdDraw.setPenColor(StdDraw.BLUE);
    StdDraw.setPenRadius(0.005);
    StdDraw.line(start, 0, stop, 0);

    if (mode.equals("line")) {
        // Affichage du signal sous forme de ligne
        for (int i = start; i < stop - 1; i++) {
            StdDraw.line(i, sig[i], i + 1, sig[i + 1]);
        }
    } else if (mode.equals("point")) {
        // Affichage du signal sous forme de points
        for (int i = start; i < stop; i++) {
            StdDraw.point(i, sig[i]);
        }
    } else {
        System.out.println("Mode non pris en charge");
    }
}

  /**
   * Un exemple de main qui doit pourvoir être exécuté avec les méthodes
   * que vous aurez conçues.
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("Usage: java DosRead <input_wav_file>");
      return;
    }
    String wavFilePath = args[0];

    // Open the WAV file and read its header
    DosRead dosRead = new DosRead();
    dosRead.readWavHeader(wavFilePath);

    // Print the audio data properties
    System.out.println("Fichier audio: " + wavFilePath);
    System.out.println("\tSample Rate: " + dosRead.sampleRate + " Hz");
    System.out.println("\tBits per Sample: " + dosRead.bitsPerSample + " bits");
    System.out.println("\tData Size: " + dosRead.dataSize + "bytes");

    // Read the audio data
    dosRead.readAudioDouble();
    // reverse the negative values
    dosRead.audioRectifier();
    // apply a low pass filter
    dosRead.audioLPFilter(44);
    // Resample audio data and apply a threshold to output only 0 & 1
    dosRead.audioResampleAndThreshold(dosRead.sampleRate / BAUDS, 12000);
    dosRead.decodeBitsToChar();
    if (dosRead.decodedChars != null) {
      System.out.print("Message décodé : ");
      printIntArray(dosRead.decodedChars);
    }

    displaySig(dosRead.audio, 0, dosRead.audio.length - 1, "line", "Signal audio");

    // Close the file input stream
    try {
      dosRead.fileInputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
