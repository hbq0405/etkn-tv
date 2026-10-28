package xyz.etkn.tv;

import org.junit.Test;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import static org.junit.Assert.*;

public class ConnectionTest {
    private static final class Server implements AutoCloseable {
        final ServerSocket socket;
        final Thread worker;
        volatile boolean closed;
        Server(String id,int mediaPort) throws Exception {
            socket=new ServerSocket(0,20,java.net.InetAddress.getByName("127.0.0.1"));
            worker=new Thread(()->{
                while(!closed)try(Socket client=socket.accept()){
                    BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));
                    String path=input.readLine().split(" ")[1],line;
                    while((line=input.readLine())!=null&&!line.isEmpty()){}
                    String body;
                    if(path.equals("/api/health"))body="{\"status\":\"ok\"}";
                    else if(path.equals("/api/auth/check_status"))body="{\"status\":\"login_required\"}";
                    else if(path.equals("/api/tv/System/Info/Public"))body="{\"Id\":\""+id+"\",\"ServerName\":\"Test\",\"ProductName\":\"ETKN\",\"TvApiVersion\":1}";
                    else body="{\"ProductName\":\"ETKN\",\"ApiPort\":"+socket.getLocalPort()+",\"MediaServerPort\":"+mediaPort+",\"MediaServerAddress\":\"http://127.0.0.1:"+mediaPort+"\"}";
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    client.getOutputStream().write(bytes);
                }catch(Exception error){if(!closed)throw new RuntimeException(error);}
            });worker.setDaemon(true);worker.start();
        }
        String address(){return "http://127.0.0.1:"+socket.getLocalPort();}
        @Override public void close()throws Exception{closed=true;socket.close();worker.join(2000);}
    }
    @Test public void managementAddressResolvesToMatchingMediaInstance() throws Exception {
        try(Server media=new Server("same",0);Server management=new Server("same",media.socket.getLocalPort())){
            assertEquals(media.address(),LanDiscovery.resolve(management.address(),2000).getString("url"));
        }
    }
    @Test public void differentMediaInstanceIsRejectedBeforeLogin() throws Exception {
        try(Server media=new Server("other",0);Server management=new Server("original",media.socket.getLocalPort())){
            try{LanDiscovery.resolve(management.address(),2000);fail("mismatched server accepted");}
            catch(Exception expected){assertTrue(expected.getMessage().contains("身份不一致"));}
        }
    }
}
