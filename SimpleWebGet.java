/* Kenneth Orton
 * ACO 330
 * Programming Assignment 
 * 10/20/2015
 * */

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.bind.DatatypeConverter;
import java.text.SimpleDateFormat;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class SimpleWebGet{
  public static void main(String[] args){
    try{
      String url = new String(args[0]);
      
      //show the system time in terminal
      Calendar calendar = Calendar.getInstance();
      SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
      System.out.println("--" + dateFormat.format(calendar.getTime()) + "-- " + url);
      
      //show the filename to be downloaded
      System.out.println("\t   => \'" + getFileName(url) + "\'");
      
      // 1 Get user input - parse the URL to get hostname
      String hostname = new String(parseHostName(args[0].toLowerCase()));
    
      // 2 Resolve the hostname to IP addresses
      System.out.print("Resolving " + hostname + "... ");
      String ipAddress = new String(resolveIP(hostname));
      System.out.print(ipAddress + '\n');

      // 3 Connect to the web server
      System.out.print("Connecting to " + hostname + "[" + ipAddress + "]:" + outgoingPort(url) + "...");
      Socket clientSocket = new Socket(ipAddress, outgoingPort(url));
      
      // 4 Create the HTTP GET request - send the request to the server
      String encodedAuth = DatatypeConverter.printBase64Binary("user:passwd".getBytes("UTF-8"));
      DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());

      if(clientSocket.isConnected()){
        System.out.print(" connected.\n");
      }

      System.out.print("HTTP request sent, awaiting response...");

      //GET request to retrieve the content of packet
      outToServer.writeBytes("GET " + parseURLPath(url) + " HTTP/1.1\r\n" +
                            "Host: " + hostname + "\r\n" + 
                            "Authorization: Basic " + encodedAuth + "\r\n" +
                            "Connection: close \r\n\r\n");


      DataInputStream inFromServer = new DataInputStream(clientSocket.getInputStream());
      ArrayList<String> headerLines = new ArrayList<String>();
      //ByteArrayOutputStream serverFile = new ByteArrayOutputStream();
      int i = 0;
      int contLength = 0;

      headerLines = strHeader(inFromServer);

      //display status code
      System.out.print(displayStatus(headerLines.get(0)));

      //handle errors according to status code
      if(parseStatusCode(headerLines.get(0)) == 404){
        System.out.println(dateFormat.format(calendar.getTime()) + " ERROR 404: Not Found.");
      }else if(parseStatusCode(headerLines.get(0)) == 400){
        System.out.println(dateFormat.format(calendar.getTime()) + " ERROR 400: Bad Request.");
      }else if(parseStatusCode(headerLines.get(0)) == 302){
        System.out.print(dateFormat.format(calendar.getTime()) + " Link has changed try again with: " + splitHeaderFields(headerLines).get("Location"));
      }else{
        //don't create file if not found
        DataOutputStream outputFile = new DataOutputStream(new FileOutputStream(getFileName(url)));
        String contentType = new String(splitHeaderFields(headerLines).get("Content-Type"));

        //start the timer
        long start = System.currentTimeMillis();
    
        //File extension is of type image
        if(contentType.substring(0, contentType.indexOf("/")).equals("image")){
          URL imageURL = new URL(url);
          BufferedImage image = ImageIO.read(imageURL);
          String imgType = new String(contentType.substring(contentType.indexOf("/") + 1).trim());

          if(imgType.equals("png")){
            ImageIO.write(image, "png", outputFile);
          }else if(imgType.equals("gif")){
            ImageIO.write(image, "gif", outputFile);
          }else if(imgType.equals("jpeg")){
            ImageIO.write(image, "jpg", outputFile);
          }else if(imgType.contains("bmp")){
            ImageIO.write(image, "bmp", outputFile);
          }
        }else{
          //download the object
          i = inFromServer.read();
          while((i = inFromServer.read()) > 0){
            outputFile.write(i);
          }
        }
    
        //stop the timer
        long transTime = System.currentTimeMillis() - start;
        double seconds = transTime / 1000.0;

        //if Content-Length was not reported
        if(splitHeaderFields(headerLines).get("Content-Length") != null){
          contLength = Integer.parseInt(splitHeaderFields(headerLines).get("Content-Length").trim());
        }else{
          contLength = outputFile.size();
        }

        //format Content Length int with commas
        String length = String.format("%,d", contLength);

        //display content length and type
        if(contentType.indexOf(";") > 0){
          System.out.println("Length: " + length + " [" + contentType.substring(0, contentType.indexOf(";")) + "]");
        }else{
          System.out.println("Length: " + length + " [" + contentType.trim() + "]");
        }

        //display transfer rate
        double kbTrans = contLength / 1000.0;
        double transRate = kbTrans / seconds;

        System.out.println(dateFormat.format(calendar.getTime()) + " (" + String.format("%.2f", transRate) + " KB/s) - '" + getFileName(url) + "' saved [" + length + "/" + length + "]");
      }

    }catch(IndexOutOfBoundsException e){
      System.out.println("\nThe program expects user input in form of URL.\n" 
                         + "Correct usage: java [program name] {url}\n");
    }catch(UnknownHostException e){
      System.out.print("failed: Host not found.\n");
    }catch(IOException e){
      System.out.println("\nConnection refused");
    }
  }

  public static String resolveIP(String hostname)throws UnknownHostException{
    InetAddress resolver = InetAddress.getByName(hostname);

    int begin = resolver.toString().indexOf("/");
    return resolver.toString().substring(begin + 1);
  }  

  public static String parseHostName(String url){
    return stripProtocol(stripPath(url));
  }

  public static String parseURLPath(String url){
    String name = new String(stripProtocol(url).substring(parseHostName(url).length()));
    //force default path
    if(name.equals("")){
      name += "/";
      name.trim();
    }
    return name;
  }

  public static String getFileName(String url){
    String name = new String(parseURLPath(url).substring(parseURLPath(url).lastIndexOf("/") + 1));
    //force default filename
    if(name.equals("")){
      name += "index.html";
      name.trim();
    }
    return name;
  }

  public static String displayStatus(String status){
    return status.substring(status.indexOf(" ")); 
  }
  
  public static String stripProtocol(String url){
    //remove http:// or https://
    int begin = url.indexOf("://");
    //if user did not enter protocol before url (www.google.com)
    if(begin == -1){
      return url;
    //if user entered protocol before url (https://www.google.com)
    }else{
      return url.substring(begin + 3);
    }

  }

  public static String stripPath(String url){
    //find where the path starts
    int end = stripProtocol(url).indexOf("/");
    //there is no path
    if(end == -1){
      return url;
    }else{
      return url.substring(0, end + (url.length() - stripProtocol(url).length()));
    }
  }

  //check user input for proper port number, default to https
  public static int outgoingPort(String url){
    //http port 443
    if((url.length() - stripProtocol(url).length()) == 8){
      return 443;
    //https port 80 
    }else{
      return 80;
    }
  }

  //get status code
  public static int parseStatusCode(String status){
    String number = "";
    String trim = status.substring(status.indexOf(" "));
    int i = 0;
    
    while(i < trim.length()){
      if(Character.isDigit(trim.charAt(i))){
        number += trim.charAt(i);
      }
      i++;
    }
    return Integer.parseInt(number);
  }

  //get lines in the header response
  public static ArrayList<String> strHeader(DataInputStream inFromServer) throws IOException{
    ArrayList<String> headerLines = new ArrayList<String>();
    ByteArrayOutputStream serverFile = new ByteArrayOutputStream();
    int i = inFromServer.read();
    int start = 0;
    int end = 0;
    
    while(i > 0){
      serverFile.write(i);
      end++;
      //if byte is carriage return insert new line
      if((i = inFromServer.read()) == 13){
        serverFile.write(10);
        i = inFromServer.read();
        end++; 
        //if next byte is another carriage return 
        //the end of header is reached
        if((i = inFromServer.read()) == 13){
          i = -1;
        }
        headerLines.add(serverFile.toString().substring(start, end));
        start = end;
      }
    }
    return headerLines;
  }

  //separate lines in header as key value pairs in map
  public static Map<String, String> splitHeaderFields(ArrayList<String> list){
    Map<String, String> headerFields = new HashMap<>();
    for(int i = 1; i < list.size(); i ++){
      headerFields.put(list.get(i).split(": ")[0], list.get(i).split(": ")[1]);
    }
    return headerFields;
  }
}


