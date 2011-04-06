package cc.osint.graphd.util;

import java.io.*;

public class TextFile {
  static public String get(String fn) {
    File aFile = new File(fn);
    StringBuilder contents = new StringBuilder();
    try {
      BufferedReader input =  new BufferedReader(new FileReader(aFile));
      try {
        String line = null;
        while (( line = input.readLine()) != null){
          contents.append(line);
          contents.append(System.getProperty("line.separator"));
        }
      }
      finally {
        input.close();
      }
    }
    catch (IOException ex){
      //ex.printStackTrace();
      return null;
    }
    return contents.toString();
  }
  
  static public void put(String fn, String aContents)
    throws FileNotFoundException, IOException {
    Writer output = new BufferedWriter(
        new FileWriter(
            new File(fn)));
    try {
      output.write( aContents );
    }
    finally {
      output.close();
    }
  }
} 

