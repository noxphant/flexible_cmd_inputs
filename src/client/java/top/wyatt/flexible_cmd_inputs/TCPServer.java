package top.wyatt.flexible_cmd_inputs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TCPServer {
    private static final Logger LOGGER = LogManager.getLogger(TCPServer.class);
    private final int port;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final List<ClientHandler> connectedClients = new ArrayList<>();

    public TCPServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (isRunning) return;
        isRunning = true;
        serverSocket = new ServerSocket(port);
        new Thread(this::acceptClients, "FCI-TCPServer").start();
        LogListener.setLogConsumer(this::broadcastLog);
    }

    private void acceptClients() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                connectedClients.add(handler);
                new Thread(handler, "FCI-ClientHandler").start();
                handler.send("=== 灵活指令输入 TCP服务 ===");
                handler.send("端口: " + port + " | 输入指令回车执行 | 输入exit断开");
            } catch (IOException e) {
                if (isRunning) LOGGER.warn("客户端连接失败", e);
            }
        }
    }

    public void broadcastLog(String log) {
        for (ClientHandler client : new ArrayList<>(connectedClients)) {
            client.send(log);
        }
    }

    public void stop() {
        isRunning = false;
        connectedClients.forEach(ClientHandler::close);
        connectedClients.clear();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            LOGGER.error("关闭TCP服务器失败", e);
        }
    }

    // 客户端处理器
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            } catch (IOException e) {
                close();
                LOGGER.error("初始化客户端Handler失败", e);
            }
        }


        @Override
        public void run() {
            String input;
            try {
                while ((input = in.readLine()) != null) {
                    String cmd = input.trim();
                    if (cmd.equalsIgnoreCase("exit")) {
                        send("断开连接...");
                        close();
                        break;
                    }
                    FlexibleCommandInputs.executeCommand(cmd);
                    send("[执行] " + cmd);
                }
            } catch (IOException e) {
                if (!socket.isClosed()) LOGGER.warn("客户端断开连接", e);
            } finally {
                close();
            }
        }

        public void send(String msg) {
            if (out != null && !socket.isClosed()) out.println(msg);
        }

        public void close() {
            connectedClients.remove(this);
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (!socket.isClosed()) socket.close();
            } catch (IOException e) {
                LOGGER.error("关闭客户端连接失败", e);
            }
        }
    }
}