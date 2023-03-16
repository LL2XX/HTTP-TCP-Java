import java.io.*;
import java.util.*;
import java.net.*;

public class ClientThread implements Runnable {
    private static String files;
    private static String ServerIP, ServerName, message;
    private static int ServerPort, threadNum, testTime;
    public static double Alpha = 0.125, aveRTT = 0;
    public static long tolBytes = 0, tolRequests = 0, tolTime = 0, tolFiles = 0;
    public void run() {
        while(true) {
            try {
                Socket client = null;
                //System.out.println("ServerIP:"+ServerIP+" Port"+ServerPort);
                client = new Socket(ServerIP, ServerPort);
                client.setSoTimeout(5000);
                PrintStream out = new PrintStream(client.getOutputStream());
                BufferedReader buf = new BufferedReader(new InputStreamReader(client.getInputStream()));  
                BufferedReader testFile = new BufferedReader(new FileReader(files));
                String tmp;
                while((tmp=testFile.readLine())!=null)
                //for(int i=0;i<files.length;i++)
                {
                    String str = "GET " + tmp.trim() + message;
                    //发送数据到服务端
                    long start = System.currentTimeMillis();
                    out.println(str);
                    tolRequests++;

                    String echo = null;
                    char[] strs = new char[512];
                    do {
                        try{
                            echo = buf.readLine();
                            long end = System.currentTimeMillis();
                            aveRTT=(1-Alpha)*aveRTT+Alpha*(end-start);

                            if (echo.startsWith("Content-Length:"))
                            {
                                //System.out.println(buf.readLine());
                                int len = Integer.parseInt(echo.split(":")[1].trim());
                                for(int rcv=0;len>0;rcv=buf.read(strs),len-=rcv)
                                    tolBytes+=rcv;
                                //while((len-=buf.read(strs))>0)
                                //    ;
                                break;
                            }
                        }catch(SocketTimeoutException e){
                            System.out.println(e);
                            break;
                        }
                    }while(true);
                    tolFiles++;
                }
                if(client != null){  
                    //如果构造函数建立起了连接，则关闭套接字，如果没有建立起连接，自然不用关闭  
                    client.close(); //只关闭socket，其关联的输入输出流也会被关闭
                }
            } catch(Exception e) {
                System.out.println(e);
                System.out.println("Socket Error");  
                break;
            }
        }
    }
    public static void main(String[] args) throws IOException {  
        ServerIP = args[1];
        ServerName = args[3];
        ServerPort = Integer.parseInt(args[5]);
        threadNum = Integer.parseInt(args[7]);
        files = args[9];
        testTime = Integer.parseInt(args[args.length-1]);
        message = " HTTP/1.0" + System.lineSeparator() +
                         "Host:" + ServerName + System.lineSeparator() + "\r\n";

        Thread[] threadPool = new Thread[threadNum];
	    long start = System.currentTimeMillis();
        for (int i=0;i<threadPool.length;i++)
        {
            ClientThread t = new ClientThread();
            threadPool[i]=new Thread(t);
            threadPool[i].start();
        }

        try {
            Thread.sleep(testTime*1000);
        } catch (Exception e) {
            System.out.println(e);
        }
	    long end = System.currentTimeMillis();
	    tolTime = end-start;
	    System.out.println("tolTime="+tolTime);
        System.out.println("aveRTT="+aveRTT);
        System.out.println("tolBytes="+tolBytes);
        System.out.println("tolRequests="+tolRequests);
        System.out.println("tolFiles="+tolFiles);
	    System.out.println("Throughout="+(tolBytes/tolTime*8.0/1000/1000));
        System.exit(0);
        //ClientThread t = new ClientThread();
        //new Thread(t).start();
    }
}
