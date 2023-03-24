package nl.buevink.screens;

import java.io.*;

public class Store {
    private final File file;
    public Store() {
        this.file = new File("config/storage.txt");
    }

    public void storeRemotePosition(int position){
        try(FileWriter writer = new FileWriter(file)) {
            writer.write(position);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int restoreRemotePosition(){

        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(!file.exists() || !file.isFile() || !file.canRead()) {
            throw new RuntimeException("Can't reach file");

        }
        try (FileReader input = new FileReader(file)){
            return input.read();
        } catch (IOException e) {
            throw new RuntimeException("File not read", e);
        }
    }

}
