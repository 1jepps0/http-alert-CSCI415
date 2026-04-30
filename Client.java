import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class Client {

  // server address, can be changed by passing it as a command line argument
  static String server = "http://localhost:8080";

  // unique name this client uses when talking to the server
  static String clientId = "";

  // used so the same alert does not open more than one window
  static int lastAlertId = -1;

  public static void main(String[] args) {
    // example: java Client http://192.168.1.10:8080
    if (args.length > 0) {
      server = args[0];
    }
    if (server.endsWith("/")) {
      server = server.substring(0, server.length() - 1);
    }

    clientId = makeClientId();

    // tell the server this client is online and show the welcome message
    register();

    // keep checking the HTTP server for alert messages
    while (true) {
      checkForAlert();
      sleep(2000);
    }
  }

  static void register() {
    try {
      // server returns: welcome=Welcome+to+the+alert+system
      String data = request("/register?client=" + encode(clientId));
      String welcome = decode(getValue(data, "welcome"));
      JOptionPane.showMessageDialog(null, welcome, "Welcome", JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      System.out.println("Register error: " + e.getMessage());
    }
  }

  static void checkForAlert() {
    try {
      // ask the server whether this client has an unacknowledged alert
      String data = request("/poll?client=" + encode(clientId));

      if ("true".equals(getValue(data, "alert"))) {
        int id = toInt(getValue(data, "id"));
        String message = decode(getValue(data, "message"));

        if (id != lastAlertId) {
          // remember this id so polling does not keep opening the same alert
          lastAlertId = id;
          showAlert(id, message);
        }
      }
    } catch (Exception e) {
      System.out.println("Poll error: " + e.getMessage());
    }
  }

  static void showAlert(final int id, String message) {
    // default message if the server sends an empty message
    if (message == null || message.length() == 0) {
      message = "ATTENTION STUDENTS!! EMERGENCY ALERT";
    }

    final String alertMessage = message;

    SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            // play a simple warning sound
            Toolkit.getDefaultToolkit().beep();
            Toolkit.getDefaultToolkit().beep();
            Toolkit.getDefaultToolkit().beep();

            // create a full screen alert window
            final JFrame frame = new JFrame("Alert");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.getContentPane().setBackground(Color.RED);
            frame.setLayout(new BorderLayout());

            JLabel label =
                new JLabel(
                    "<html><center>" + escapeHtml(alertMessage) + "</center></html>",
                    SwingConstants.CENTER);
            label.setForeground(Color.YELLOW);
            label.setFont(new Font("Serif", Font.BOLD, 50));

            JButton button = new JButton("Acknowledge");
            button.setFont(new Font("SansSerif", Font.BOLD, 28));
            button.addActionListener(
                new ActionListener() {
                  public void actionPerformed(ActionEvent e) {
                    // tell the server this alert was received, then close the window
                    acknowledge(id);
                    frame.dispose();
                  }
                });

            frame.add(label, BorderLayout.CENTER);
            frame.add(button, BorderLayout.SOUTH);
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
          }
        });
  }

  static void acknowledge(int id) {
    try {
      // send acknowledgement for this specific alert id
      request("/ack?client=" + encode(clientId) + "&id=" + id);
    } catch (Exception e) {
      System.out.println("Ack error: " + e.getMessage());
    }
  }

  static String request(String path) throws IOException {
    // opens a socket and sends a basic HTTP GET request manually
    String host = server;
    int port = 80;
    if (host.startsWith("http://")) {
      host = host.substring(7);
    }

    int colon = host.indexOf(':');
    if (colon >= 0) {
      port = toInt(host.substring(colon + 1));
      host = host.substring(0, colon);
    }

    Socket socket = new Socket(host, port);
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
    writer.print("GET " + path + " HTTP/1.1\r\n");
    writer.print("Host: " + host + "\r\n");
    writer.print("Connection: close\r\n");
    writer.print("\r\n");
    writer.flush();

    BufferedReader reader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
    StringBuilder data = new StringBuilder();

    String line;
    while ((line = reader.readLine()) != null && line.length() > 0) {}

    while ((line = reader.readLine()) != null) {
      data.append(line).append("\n");
    }

    reader.close();
    socket.close();
    return data.toString();
  }

  static String getValue(String data, String name) {
    // finds one value from response text like: id=1
    String[] lines = data.split("\n");
    for (int i = 0; i < lines.length; i++) {
      int equals = lines[i].indexOf('=');
      if (equals > 0 && lines[i].substring(0, equals).equals(name)) {
        return lines[i].substring(equals + 1);
      }
    }
    return "";
  }

  static String makeClientId() {
    // hostname plus current time makes a simple unique client id
    String host = "localhost";
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      host = "localhost";
    }
    return host + "-" + System.currentTimeMillis();
  }

  static int toInt(String value) {
    // convert response id text into a number
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return -1;
    }
  }

  static String encode(String value) throws UnsupportedEncodingException {
    // encode values before putting them in a URL
    return URLEncoder.encode(value, "UTF-8");
  }

  static String decode(String value) throws UnsupportedEncodingException {
    // decode values received from the server
    if (value == null) {
      return "";
    }
    return URLDecoder.decode(value, "UTF-8");
  }

  static String escapeHtml(String value) {
    // prevent message text from being treated as HTML
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static void sleep(int ms) {
    // pause between polls so the client does not spam the server
    try {
      Thread.sleep(ms);
    } catch (Exception e) {
      // nothing to do if sleep is interrupted
    }
  }
}
