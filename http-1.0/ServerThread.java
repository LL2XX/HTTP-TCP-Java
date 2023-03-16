import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

class WebRequestHandler implements Runnable {
    static boolean _DEBUG = true;
    static int     reqCount = 0;
    //private int conCurrentMod = 2;
    SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",Locale.US);
    String WWW_ROOT;
    ServerSocket welcomeSocket;
    BufferedReader inFromClient;
    DataOutputStream outToClient;
    private Socket s = null;
    List<Socket> pool = null;

    String urlName;
    String fileName;
    File fileInfo;
    String rfc1123;
    String agent, modified;

    public WebRequestHandler(ServerSocket welcomeSocket, 
			     String WWW_ROOT) throws Exception
    {
        reqCount ++;
        this.WWW_ROOT = WWW_ROOT;
        this.welcomeSocket = welcomeSocket;
    }
    public WebRequestHandler(List<Socket> pool,
		             String WWW_ROOT) throws Exception
    {
	    reqCount ++;
	    this.pool = pool;
	    this.WWW_ROOT = WWW_ROOT;
    }

    public void run() 
    {
        System.out.println("Thread " + this + " started.");
	    if(pool==null)
	        while (true) {
	            // get a new request connection
	            synchronized (welcomeSocket) {
	    	        try {
	    	            s = welcomeSocket.accept();
	    	            System.out.println("Thread " + this 
	    			                       + " process request " + s);
	    	        } catch (IOException e) {
                        System.out.println("Server construction failed.");
	    	        }
	            } // end of extract a request
                serveARequest(s);
	        } // end while
	    else
            while (true) {
	        // get a new request connection
	    	    s = null;
	            while (s == null) {
	                synchronized(pool) {
	                    if (!pool.isEmpty()) {
                            // remove the first request
                            s = (Socket) pool.remove(0);
                            //System.out.println(pool);
                            System.out.println("Thread " + this 
	                               + " process request " + s);
	                    } // end if
	                }
	            } // end while
	            serveARequest( s );
	        } // end while(true)
    } // end of processARequest

    private void serveARequest(Socket connSocket) {
	    // take a ready connection from the accepted queue
	    System.out.println("\nReceive request from " + connSocket);
        try {
            connSocket.setSoTimeout(5000);
	        inFromClient =
	          new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
	        outToClient =
	          new DataOutputStream(connSocket.getOutputStream());
        } catch (Exception e) {
	        System.out.println("Timout Error");
            System.out.println(e);
            return;
        }
        while(true) {
    	    try {
                //System.out.println("start process");
    	        mapURL2File();
    	    	outputResponseHeader();
    	        if ( fileInfo != null ) // found the file and knows its info
    	        {
    	    	    outputResponseBody();
    	        } // dod not handle error
    	    } catch (Exception e) {
                System.out.println(e);
    	        outputError(400, "Server error");
                break;
    	    }
        }
        try {
    	    connSocket.close();
        }
        catch (Exception e) {
            System.out.println("close Failed");
            System.out.println(e);
        }
    }

    private void mapURL2File() throws Exception 
    {
	    String requestMessageLine = inFromClient.readLine();
	    //DEBUG("Request " + reqCount + ": " + requestMessageLine);
	    // process the request
        String[] line = requestMessageLine.split("\\s");
	    if (line.length < 2 || !line[0].equals("GET"))
	    {
		    outputError(500, "Bad request");
		    return;
	    }
	    // parse URL to retrieve file name
	    urlName = line[1];

        while(!((requestMessageLine = inFromClient.readLine()).isEmpty()))
        {
            //DEBUG(requestMessageLine);
            if (requestMessageLine.contains("If-Modified-Since"))
                modified = requestMessageLine.split(": ")[1].trim();
            else if (requestMessageLine.contains("User-Agent"))
                agent = requestMessageLine.split(":")[1].trim();
        }
	    
        // debugging
        /*if (_DEBUG) {
           String line = inFromClient.readLine();
           while ( !line.equals("") ) {
              DEBUG( "Header: " + line );
              line = inFromClient.readLine();
           }
        }*/

	    // map to file name
        if ( urlName.equals("/") && ( agent.contains("iPhone") || agent.contains("Android") ) )
        {
            urlName = "/index_m.html";
            fileInfo = new File( WWW_ROOT + urlName );
	        if ( !fileInfo.exists() )
                urlName = "/index.html";
        }
        else if ( urlName.endsWith("/") )
            urlName = "/index.html";
	    fileName = WWW_ROOT + urlName;
	    //DEBUG("Map to File name: " + fileName);

        if (ServerThread.cache.containsKey(fileName))
            fileInfo = ServerThread.cache.get(fileName);
        else
        {
            fileInfo = new File(fileName);
            long cnt = 0;
            for (File file: ServerThread.cache.values())
                cnt += file.length();
            if ((cnt+fileInfo.length())/1024 < ServerThread.cacheSize)
                ServerThread.cache.put(fileName, fileInfo);
        }

	    if ( !fileInfo.exists() ) 
	    {
            DEBUG("404");
		    outputError(404, "Not Found\r\n");
		    fileInfo = null;
	    }
        else if ( modified!=null && sdf.parse(modified).after(new Date(fileInfo.lastModified()) ) )
        {
            DEBUG("304");
            outputError(304, "Not Modified\r\n");
            fileInfo = null;
        }
        else 
        {
            //DEBUG("200");
	        outToClient.writeBytes("HTTP/1.0 200 OK\r\n");
        }
        //DEBUG("file end");

    } // end mapURL2file


    private void outputResponseHeader() throws Exception 
    {
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        rfc1123 = sdf.format(new Date());
        outToClient.writeBytes("Date: "+rfc1123+"\r\n");
        outToClient.writeBytes("Server: "+ServerThread.servername+"\r\n");
	    //outToClient.writeBytes("Set-Cookie: MyCool433Seq12345\r\n");

        if (fileInfo!=null) {
	        if (urlName.endsWith(".jpg"))
	            outToClient.writeBytes("Content-Type: image/jpeg");
	        else if (urlName.endsWith(".gif"))
	            outToClient.writeBytes("Content-Type: image/gif");
	        else if (urlName.endsWith(".html") || urlName.endsWith(".htm"))
	            outToClient.writeBytes("Content-Type: text/html");
            outToClient.writeBytes("\r\nContent-Length: "+fileInfo.length()+"\r\n");
        }
        else
            outToClient.writeBytes("Content-Length: 0\r\n");
        outToClient.writeBytes("\r\n");
    }

    private void outputResponseBody() throws Exception 
    {
        ArrayList<String> cgiFiles = new ArrayList<String>(Arrays.asList("py", "exe", "java", "bat", "jar"));
        String[] tmp = fileInfo.getName().split("\\.");
        String suffix = tmp[tmp.length-1].trim();
        if(fileInfo!=null)
        {
            if(cgiFiles.contains(suffix)) {
                var processBuilder = new ProcessBuilder();
                var env = processBuilder.environment();
                env.put("QUERY_STRING", "");
                env.put("REMOTE_ADDR", s.getInetAddress().getHostAddress());
                env.put("REQUEST_METHOD", "GET");
                env.put("SERVER_NAME", ServerThread.servername);
                env.put("SERVER_PROTOCOL", "HTTP/1.0");
                env.put("SERVER_PORT", String.valueOf(ServerThread.serverPort));

                switch (suffix) {
                    case "jar": case "bat": case "exe":
                        processBuilder.command(fileInfo.getName());
                        break;
                    case "py":
                        processBuilder.command("python",fileInfo.getName());
                        break;
                    case "java":
                        processBuilder.command("java",fileInfo.getName());
                        break;
                }
                var process = processBuilder.start();
                try (var reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outToClient.writeBytes(line);
                    }
                }
            }
            else {
                int numOfBytes = (int) fileInfo.length();

                // send file content
                FileInputStream fileStream  = new FileInputStream (fileName);

                byte[] fileInBytes = new byte[numOfBytes];
                fileStream.read(fileInBytes);

                outToClient.write(fileInBytes, 0, numOfBytes);
                outToClient.writeBytes("\r\n");
            }
        }
    }

    void outputError(int errCode, String errMsg)
    {
	    try {
	        outToClient.writeBytes("HTTP/1.0 " + errCode + " " + errMsg);
	    } catch (Exception e) {
            System.out.println(e);
            System.out.println("outputError Failed");
        }
    }

    void DEBUG(String s) 
    {
       if (_DEBUG)
          System.out.println( s );
    }
}

public class ServerThread{

    public static String servername = "wkw.httptest.com";
    public static int serverPort = 6789;
    public static int thread_cnt = 3;
    public static String WWW_ROOT = "./";
    public static ServerSocket welcomeSocket = null;

    public static String configFile = "httpd.conf";
    public static Map<String, File> cache = new HashMap<>();
    public static int cacheSize = 1024*8;
    private static Thread[] threads;
    private static List<Socket> connSockPool = new ArrayList<>();

    public static void ShareWelcome() {
	    try {
            threads = new Thread[thread_cnt];
	        // process a request
            for (int i = 0; i < threads.length; i++) {
                WebRequestHandler handler = new WebRequestHandler(welcomeSocket,WWW_ROOT);
                threads[i] = new Thread(handler);
                threads[i].start();
            }
            for (int i = 0; i < threads.length; i++)
                threads[i].join();
	    } catch (Exception e) {
            	System.out.println(e);
            	System.out.println("Server construction failed.");
	    }
    }

    public static void ShareQ() {
        try {
        	// create thread ServerThread.connSockPool
        	connSockPool = new Vector<Socket>();
        	threads = new Thread[thread_cnt];
        	// start all threads
        	for (int i = 0; i < threads.length; i++) {
                WebRequestHandler handler = new WebRequestHandler(connSockPool,WWW_ROOT);
        	    threads[i] = new Thread(handler); 
        	    threads[i].start();
        	}
        } catch (Exception e) {
            System.out.println(e);
	        System.out.println("Server construction failed.");
        } // end of catch
        while (true) {
        	try {
                System.out.println("Start Welcome");
        	    // accept connection from connection queue
        	    Socket connSock = welcomeSocket.accept();
        	    System.out.println("Main thread retrieve connection from " 
        		                   + connSock);
        
        	    // how to assign to an idle thread?
        	    synchronized (connSockPool) {
        	        connSockPool.add(connSock);
        	    } // end of sync
        	} catch (Exception e) {
                System.out.println(e);
        		System.out.println("server run failed.");
        	} // end of catch
        } // end of loop
    }

    public static void main(String args[]) throws Exception  {

	    // see if we do not use default server port
	    if (args.length >= 1)
	        servername = args[0];
	    // see if we want a different root
	    if (args.length >= 3)
	        configFile = args[2];
        BufferedReader config = new BufferedReader(new FileReader(configFile));

        String line=null;
        while((line=config.readLine())!=null)
        {
            line=line.trim();
            if(line.contains("Listen"))
                serverPort = Integer.parseInt(line.split(" ")[1].trim());
            else if (line.contains("CacheSize"))
                cacheSize = Integer.parseInt(line.split(" ")[1].trim());
            else if (line.contains("ThreadPoolSize"))
                thread_cnt = Integer.parseInt(line.split(" ")[1].trim());
            else if (line.contains("DocumentRoot"))
            {
                String tmp = null;
                if((tmp=config.readLine())!=null && tmp.contains("ServerName") && tmp.trim().split(" ")[1].trim().equals(servername))
                    WWW_ROOT = line.split(" ")[1].trim();
            }
        }

	    // create server socket
	    welcomeSocket = new ServerSocket(serverPort);
        //ShareWelcome();
        ShareQ();
	    
    } // end of main

} // end of class WebServer
