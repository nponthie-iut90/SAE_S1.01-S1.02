import java.io.*;

public class DosRead {
    static final int FP = 1000; // Fréquence de la porteuse
    static final int BAUDS = 100; // Débit en symboles par seconde
    static final int[] START_SEQ = {1, 0, 1, 0, 1, 0, 1, 0}; // Séquence de synchro au début
    FileInputStream fileInputStream; // Flux d'entrée du fichier
    int sampleRate = 44100; // Fréquence d'échantillonnage
    int bitsPerSample; // Nombre de bits par échantillon
    int dataSize; // Taille des données audio
    double[] audio; // Tableau de données audio en double
    int[] outputBits; // Tableau des bits de sortie
    char[] decodedChars; // Tableau des caractères décodés

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
            fileInputStream.read(audioData); // Lecture des données audio
        } catch (IOException e) {
            e.printStackTrace(); // Affiche les erreurs d'entrée/sortie
        }

        audio = new double[dataSize / 2]; // Initialisation du tableau audio

        bitsPerSample = 16; // Bits par échantillon

        for (int i = 0; i < dataSize / 2; i++) {
            int sample = (audioData[2 * i + 1] << 8) | (audioData[2 * i] & 0xFF); // Conversion little-endian
            audio[i] = sample / 32768.0; // Normalisation entre -1 et 1
        }
    }


    /**
     * Reverse the negative values of the audio array
     */
    public void audioRectifier() {
        for (int i = 0; i < audio.length; i++) {
            if (audio[i] < 0) {
                audio[i] = -audio[i]; // Inversion des valeurs négatives
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
        double[] filteredAudio = new double[audio.length]; // Tableau pour stocker les échantillons audio filtrés

        // Applique un filtre passe-bas (moyenne mobile)
        for (int i = 0; i < audio.length; i++) { // Parcourt les échantillons audio
            double sum = 0.0; // Initialise la somme pour calculer la moyenne
            int start = Math.max(0, i - n + 1); // Calcul de l'indice de début de la fenêtre
            int end = i + 1; // Calcul de l'indice de fin de la fenêtre

            for (int j = start; j < end; j++) { // Boucle dans la fenêtre
                sum += audio[j]; // Somme des échantillons dans la fenêtre
            }

            filteredAudio[i] = sum / (double) (end - start); // Calcul de la moyenne et stockage dans le tableau filtré
        }

        audio = filteredAudio; // Met à jour le tableau audio avec les valeurs filtrées
    }

    /**
     * Resample the audio array and apply a threshold
     *
     * @param period    the number of audio samples by symbol
     * @param threshold the threshold that separates 0 and 1
     */
    public void audioResampleAndThreshold(int period, int threshold) {
        int numSymbols = audio.length / period; // Calcule le nombre de symboles
        outputBits = new int[numSymbols]; // Tableau pour stocker les bits de sortie

        for (int i = 0; i < numSymbols; i++) { // Parcourt les symboles
            int start = i * period; // Début de la période
            int end = start + period; // Fin de la période

            // Calcul de la somme des échantillons dans la période
            double sum = 0;
            for (int j = start; j < end; j++) { // Parcourt les échantillons
                sum += audio[j]; // Somme des échantillons
            }

            int MAX_AMP = (int) Math.pow(2, bitsPerSample); // Calcul de l'amplitude maximale

            // Calcul de la moyenne avec la plage d'amplitude
            double average = MAX_AMP * (sum / period);

            // Applique le seuillage
            outputBits[i] = (average > threshold) ? 1 : 0; // Stocke le résultat binaire
        }

        // Affiche le tableau outputBits dans le terminal
        System.out.print("outputBits: ");
        for (int i = 0; i < outputBits.length; i++) { // Parcourt les bits de sortie
            System.out.print(outputBits[i] + " "); // Affiche chaque bit
        }
        System.out.println(); // Nouvelle ligne pour la lisibilité
    }


    /**
     * Decode the outputBits array to a char array
     * The decoding is done by comparing the START_SEQ with the actual beginning of
     * outputBits.
     * The next first symbol is the first bit of the first char.
     */
    public void decodeBitsToChar() {
        int startSeqIndex = -1; // Index de la séquence de début

        // Recherche de la séquence de début dans le tableau outputBits
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
            System.out.println("Séquence de début non trouvée dans le message.");
            return;
        }

        // Calcul du nombre de symboles dans le message (8 bits par caractère)
        int numMessageSymbols = (outputBits.length - startSeqIndex) / 8;

        // Initialisation du tableau decodedChars pour stocker les caractères décodés
        decodedChars = new char[numMessageSymbols];

        // Décodage des bits en utilisant la stratégie de rééchantillonnage
        for (int i = 0; i < numMessageSymbols; i++) {
            int start = startSeqIndex + i * 8;
            int end = start + 8;

            // Conversion des 8 bits en un entier (traitement des bits à l'envers)
            int decodedValue = 0;
            for (int j = end - 1; j >= start; j--) {
                decodedValue = (decodedValue << 1) | outputBits[j];
            }

            // Conversion de l'entier en caractère ASCII
            decodedChars[i] = (char) decodedValue;
        }

        // Affichage des caractères décodés pour inspection
        System.out.print("Caractères décodés: ");
        for (char c : decodedChars) {
            System.out.print(c);
        }
        System.out.println(); // Nouvelle ligne pour la lisibilité
    }



    /**
     * Print the elements of an array
     *
     * @param data the array to print
     */
    public static void printIntArray(char[] data) {
        for (char c : data) {
            System.out.print(c); // Affichage du caractère
        }
        System.out.println(); // Nouvelle ligne pour la lisibilité
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
        StdDraw.setCanvasSize(1200, 900); // Définition de la taille de la fenêtre graphique
        StdDraw.setXscale(start, stop); // Définition de l'échelle sur l'axe x
        StdDraw.setYscale(-1.1, 1.1); // Définition de l'échelle sur l'axe y
        StdDraw.setTitle(title); // Définition du titre de la fenêtre graphique

        // Affichage de l'échelle de graduation sur l'axe x
        for (int i = start; i <= stop; i += (stop - start) / 10) {
            StdDraw.text(i, -1.0, String.valueOf(i)); // Affichage des graduations
            StdDraw.line(i, -0.02, i, 0.02); // Ligne pour les graduations
        }

        // Ajout d'une ligne bleue au milieu de la fenêtre graphique
        StdDraw.setPenColor(StdDraw.BLUE); // Couleur de la ligne
        StdDraw.setPenRadius(0.005); // Épaisseur de la ligne
        StdDraw.line(start, 0, stop, 0); // Ligne au milieu

        if (mode.equals("line")) {
            // Affichage du signal sous forme de ligne
            for (int i = start; i < stop - 1; i++) {
                StdDraw.line(i, sig[i], i + 1, sig[i + 1]); // Tracé des lignes entre les points successifs
            }
        } else if (mode.equals("point")) {
            // Affichage du signal sous forme de points
            for (int i = start; i < stop; i++) {
                StdDraw.point(i, sig[i]); // Affichage des points du signal
            }
        } else {
            System.out.println("Mode non pris en charge"); // Affichage d'un message si le mode n'est pas reconnu
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
