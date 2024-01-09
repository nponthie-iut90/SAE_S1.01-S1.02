import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Scanner;

public class DosSend {

    final int FECH = 44100; // fréquence d'échantillonnage
    final int FP = 1000; // fréquence de la porteuse
    final int BAUDS = 100; // débit en symboles par seconde
    final int FMT = 16; // format des données
    final int MAX_AMP = (1 << (FMT - 1)) - 1; // amplitude max en entier
    final int CHANNELS = 1; // nombre de voies audio (1 = mono)
    final int[] START_SEQ = { 1, 0, 1, 0, 1, 0, 1, 0 }; // séquence de synchro au début
    final Scanner input = new Scanner(System.in); // pour lire le fichier texte

    long taille; // nombre d'octets de données à transmettre
    double duree; // durée de l'audio
    double[] dataMod; // données modulées
    char[] dataChar; // données en char
    FileOutputStream outStream; // flux de sortie pour le fichier .wav

    /**
     * Constructor
     *
     * @param path the path of the wav file to create
     */
    public DosSend(String path) {
        File file = new File(path); // Création d un nouvel objet fichier à partir du chemin spécifié
        try {
            outStream = new FileOutputStream(file); //Initialise un flux de sortie vers le fichier
        } catch (Exception e) {
            System.out.println("Erreur de création du fichier"); // Affiche un message d'erreur
        }
    }

    /**
     * Write a raw 4-byte integer in little endian
     *
     * @param octets     the integer to write
     * @param taille     the size to write
     * @param destStream the stream to write in
     */
    public void writeLittleEndian(int octets, int taille, FileOutputStream destStream) {
        char poidsFaible;
        while (taille > 0) {
            poidsFaible = (char) (octets & 0xFF);
            try {
                destStream.write(poidsFaible);
            } catch (Exception e) {
                System.out.println("Erreur d'écritured");
            }
            octets = octets >> 8;
            taille--;
        }
    }

    /**
     * Create and write the header of a wav file
     */
    public void writeWavHeader() {
        taille = (long) (FECH * duree);
        long nbBytes = taille * CHANNELS * FMT / 8;
        try {
            outStream.write(new byte[] { 'R', 'I', 'F', 'F' });
            writeLittleEndian((int) (taille + 36), 4, outStream); // Taille totale du fichier - 8
            outStream.write(new byte[] { 'W', 'A', 'V', 'E' });
            outStream.write(new byte[] { 'f', 'm', 't', ' ' });
            writeLittleEndian(16, 4, outStream); // Taille du format PCM
            writeLittleEndian(1, 2, outStream); // Format PCM
            writeLittleEndian(CHANNELS, 2, outStream);
            writeLittleEndian(FECH, 4, outStream);
            writeLittleEndian(FECH * CHANNELS * FMT / 8, 4, outStream); // Taux d'octets par seconde (byte rate)
            writeLittleEndian(CHANNELS * FMT / 8, 2, outStream); // Alignement des blocs (block align)
            writeLittleEndian(FMT, 2, outStream); // Bits par échantillon (bits per sample)
            outStream.write(new byte[] { 'd', 'a', 't', 'a' });
            writeLittleEndian((int) nbBytes, 4, outStream); // Taille des données

        } catch (Exception e) {
            System.out.printf(e.toString());
        }
    }

    /**
     * Write the data in the wav file
     * after normalizing its amplitude to the maximum value of the format (16 bits
     * signed)
     */
    public void writeNormalizeWavData() {
        try {
            if (dataMod == null) {
                System.out.println("Erreur : Les données modulées ne sont pas disponibles.");
                return;
            }

            // Écriture des données normalisées dans le fichier .wav
            for (double sample : dataMod) {
                // Normalisation de l'amplitude au format FMT bits avec MAX_AMP
                // Convertir l'échantillon en format PCM 16 bits (int)
                int normalizedSample = (int) (sample * MAX_AMP);
                writeLittleEndian(normalizedSample, FMT / 8, outStream);
            }

            // Fermeture du flux de sortie
            outStream.close();
        } catch (Exception e) {
            System.out.println("Erreur d'écritureddd");
        }
    }

    /**
     * Read the text data to encode and store them into dataChar
     *
     * @return the number of characters read
     */
    public int readTextData() {
        String message = input.nextLine(); // Lecture du message entré par l'utilisateur
        dataChar = message.toCharArray(); // Conversion du message en tableau de caractères
        return dataChar.length; // Renvoie de la longueur du tableau de caractères
    }

    /**
     * convert a char array to a bit array
     *
     * @param chars
     * @return byte array containing only 0 & 1
     */
    public byte[] charToBits(char[] chars) {
        int totalBits = chars.length * 8 + START_SEQ.length; // Calcul du nombre total de bits
        byte[] result = new byte[totalBits]; // Initialisation du tableau pour stocker les bits

        // Ajouter la séquence de départ
        for (int i = 0; i < START_SEQ.length; i++) {
            result[i] = (byte) START_SEQ[i]; // Ajout de la séquence de départ dans le résultat
        }

        // Convertir les caractères en bits
        for (int i = 0; i < chars.length; i++) {
            char currentChar = chars[i];
            for (int j = 0; j < 8; j++) {
                result[i * 8 + START_SEQ.length + j] = (byte) ((currentChar >> (7 - j)) & 1);
            }
        }

        return result; // Renvoie le tableau contenant les bits convertis
    }

    /**
     * Modulate the data to send and apply the symbol throughput via BAUDS and FECH.
     *
     * @param bits the data to modulate
     */
    public void modulateData(byte[] bits) {
        dataMod = new double[bits.length * FECH / BAUDS]; // Initialisation de dataMod

        // Calcul de la fréquence angulaire de la porteuse
        double omegaP = 2 * Math.PI * FP / FECH;

        // Modulation ASK (Amplitude Shift Keying)
        for (int i = 0; i < bits.length; i++) {
            double amplitude = bits[i] == 1 ? 1.0 : 0.0; // 1 correspond à une amplitude maximale, 0 correspond à aucune amplitude
            for (int j = 0; j < FECH / BAUDS; j++) {
                dataMod[i * FECH / BAUDS + j] = amplitude * Math.sin(omegaP * j); // Modulation de l'amplitude de la porteuse
            }
        }
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
        StdDraw.setCanvasSize(800, 400); // Définition de la taille du canvas
        StdDraw.setXscale(start, stop); // Définition de l'échelle sur l'axe x
        StdDraw.setYscale(-1.1, 1.1); // Définition de l'échelle sur l'axe y
        StdDraw.setTitle(title); // Définition du titre de la fenêtre

        // Affichage de l'échelle de graduation sur l'axe x
        for (int i = start; i <= stop; i += (stop - start) / 10) {
            StdDraw.text(i, -1.0, String.valueOf(i)); // Affichage des valeurs sur l'axe x
            StdDraw.line(i, -0.02, i, 0.02); // Affichage des traits de graduation sur l'axe x
        }

        // Ajout d'une ligne bleue au milieu de la fenêtre graphique
        StdDraw.setPenColor(StdDraw.BLUE); // Définition de la couleur du stylo
        StdDraw.setPenRadius(0.005); // Définition de l'épaisseur du trait
        StdDraw.line(start, 0, stop, 0); // Dessin de la ligne horizontale bleue

        if (mode.equals("line")) {
            // Affichage du signal sous forme de ligne
            for (int i = start; i < stop - 1; i++) {
                StdDraw.line(i, sig[i], (double) i + 1, sig[i + 1]); // Dessin de la ligne entre deux points successifs
            }
        } else if (mode.equals("point")) {
            // Affichage du signal sous forme de points
            for (int i = start; i < stop; i++) {
                StdDraw.point(i, sig[i]); // Dessin d'un point pour chaque échantillon du signal
            }
        } else {
            System.out.println("Mode non pris en charge"); // Affichage d'un message si le mode n'est pas reconnu
        }
    }

    /**
     * Display signals in a window
     *
     * @param listOfSigs a list of the signals to display
     * @param start      the first sample to display
     * @param stop       the last sample to display
     * @param mode       "line" or "point"
     * @param title      the title of the window
     */
    public static void displaySig(List<double[]> listOfSigs, int start, int stop, String mode, String title) {
        // À compléter - Affichage des signaux dans une fenêtre graphique
    }

    // Le reste du code reste inchangé
    public static void main(String[] args) {
        // créé un objet DosSend
        DosSend dosSend = new DosSend("DosOok_message.wav");
        // lit le texte à envoyer depuis l'entrée standard
        // et calcule la durée de l'audio correspondant
        dosSend.duree = (dosSend.readTextData() + (double) dosSend.START_SEQ.length / 8) * 8.0 / dosSend.BAUDS;
        // génère le signal modulé après avoir converti les données en bits
        dosSend.modulateData(dosSend.charToBits(dosSend.dataChar));
        // écrit l'entête du fichier wav
        dosSend.writeWavHeader();
        // écrit les données audio dans le fichier wav
        dosSend.writeNormalizeWavData();
        // affiche les caractéristiques du signal dans la console
        System.out.println("Message : " + String.valueOf(dosSend.dataChar));
        System.out.println("\tNombre de symboles : " + dosSend.dataChar.length);
        System.out.println("\tNombre d'échantillons : " + dosSend.dataMod.length);
        System.out.println("\tDurée : " + dosSend.duree + " s");
        System.out.println();
        // exemple d'affichage du signal modulé dans une fenêtre graphique
        displaySig(dosSend.dataMod, 1000, 3000, "line", "Signal modulé");
    }
}
