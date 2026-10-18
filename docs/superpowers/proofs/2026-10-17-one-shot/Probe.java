import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/** Tries one connect to 127.0.0.1 for each port argument, and prints the result. */
public class Probe {
    public static void main(String[] ports) {
        for (String port : ports) {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(port)));
                System.out.println(port + " connected");
            } catch (Exception e) {
                System.out.println(port + " denied: " + e);
            }
        }
    }
}
