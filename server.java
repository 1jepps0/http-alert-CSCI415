import java.io.*;
import java.net.*;

public class simpleUDPServer {

  public static void main(String[] args) throws IOException {

    // get a datagram socket
    DatagramSocket s1 = new DatagramSocket(1327); // bind to port 1327

    while (true) {

      // receive a packet
      byte[] buf = new byte[256];
      DatagramPacket packet = new DatagramPacket(buf, buf.length);
      s1.receive(packet);

      // display response
      String received = new String(packet.getData(), 0, packet.getLength());
      System.out.println("Data received from " + packet.getAddress() + ":  " + received);
    }
  }
}
