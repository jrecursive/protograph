/*
 * Copyright 2011 John Muellerleile
 *
 * This file is licensed to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

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
      ex.printStackTrace();
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

