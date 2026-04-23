import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class UDPGUI {

  public static final int MAX_PACKET_SIZE = 65507;

  public UDPGUI(int port) {

    try {

      // create your udp server listen the port number and wait for a packet
      DatagramSocket s = new DatagramSocket(port);

      // wait for an UDP packet
      byte[] buf = new byte[256];
      DatagramPacket packet = new DatagramPacket(buf, buf.length);
      s.receive(packet);

      // display an alert window when a packet is received
      javax.swing.SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              showAlert();
            }
          });

      // close the server
      s.close();

    } catch (Exception e) {

      System.err.println(e.getMessage());
    }
  }

  /*
   *    Show the alert window
   */
  private void showAlert() {
    // Set a frame  and a label
    JFrame frame = new JFrame("Alert ........");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().setBackground(Color.RED);
    JLabel message = new JLabel("ATTENTION STUDENTS!! EMERGENCY ALERT");
    message.setForeground(Color.YELLOW);
    message.setFont(new Font("Serif", Font.BOLD, 50));
    frame.getContentPane().add(message, BorderLayout.CENTER);
    frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    frame.setUndecorated(true);
    frame.setAlwaysOnTop(true);
    frame.setVisible(true);
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {

    // This simple program will pop up an alert window when it receive a packet
    // from certain port
    UDPGUI p = new UDPGUI(1327);
  }
}
