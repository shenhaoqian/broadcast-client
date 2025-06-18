import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;

public class BroadcastClient {
    private static Socket socket;
    private static final int BROADCAST_PORT = 8888;
    private static final int DISCOVERY_TIMEOUT = 5000; // 延长到5秒
    private static final int MAX_RETRIES = 3; // 最大重试次数

    public static void main(String[] args) {
        // 先尝试自动发现服务端
        String serverAddress = discoverServerWithRetry();
        
        if (serverAddress != null) {
            // 自动连接成功
            if (connectToServer(serverAddress)) {
                showControlFrame();
                return;
            }
        }
        
        // 自动发现失败，使用手动连接
        if (!manualConnect()) {
            return;
        }
        
        showControlFrame();
    }
    
    // 带重试机制的发现方法
    private static String discoverServerWithRetry() {
        System.out.println("开始自动发现服务端...");
        for (int i = 0; i < MAX_RETRIES; i++) {
            System.out.println("尝试发现服务端 (" + (i + 1) + "/" + MAX_RETRIES + ")...");
            String result = discoverServer();
            if (result != null) {
                System.out.println("成功发现服务端: " + result);
                return result;
            }
            
            // 等待后重试
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        System.out.println("自动发现服务端失败");
        return null;
    }
    
    // 发现服务端方法
    private static String discoverServer() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            // 绑定到所有接口
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(BROADCAST_PORT));
            socket.setBroadcast(true);
            socket.setSoTimeout(DISCOVERY_TIMEOUT);
            
            System.out.println("监听广播端口: " + BROADCAST_PORT);
            
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            try {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("收到广播消息: " + message);
                
                // 解析广播消息
                String[] parts = message.split("\\|");
                if (parts.length == 3 && "AudioServerDiscovery".equals(parts[0])) {
                    String ip = parts[1];
                    String port = parts[2];
                    return ip + ":" + port;
                } else {
                    System.out.println("无效的广播消息格式");
                }
            } catch (SocketTimeoutException e) {
                System.out.println("搜索超时，未收到广播");
            } catch (IOException e) {
                System.err.println("接收广播时出错: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("创建广播接收套接字失败: " + e.getMessage());
        }
        return null;
    }
    
    // 手动连接方法
    private static boolean manualConnect() {
        String defaultAddress = "192.168.137.2:8899"; // 默认使用热点IP
        String serverAddress = (String) JOptionPane.showInputDialog(null, 
            "自动发现服务端失败！\n请输入服务器地址 (IP:端口)\n默认: 192.168.137.2:8899", 
            "广播系统连接", 
            JOptionPane.QUESTION_MESSAGE, null, null, defaultAddress);
        
        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            return false;
        }
        
        return connectToServer(serverAddress);
    }
    
    // 连接服务端方法
    private static boolean connectToServer(String serverAddress) {
        String[] parts = serverAddress.split(":");
        if (parts.length != 2) {
            JOptionPane.showMessageDialog(null, "地址格式错误！应为 IP:端口", "错误", 
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        try {
            String ip = parts[0].trim();
            int port = Integer.parseInt(parts[1].trim());
            
            Socket testSocket = new Socket();
            testSocket.connect(new InetSocketAddress(ip, port), 3000);
            socket = testSocket;
            
            JOptionPane.showMessageDialog(null, "成功连接到: " + ip + ":" + port, 
                "连接成功", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "连接失败: " + e.getMessage() + 
                "\n请检查:\n1. 服务端是否运行\n2. 防火墙设置\n3. IP地址是否正确", 
                "连接错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    private static void showControlFrame() {
        SwingUtilities.invokeLater(() -> {
            ControlFrame frame = new ControlFrame(socket);
            frame.setVisible(true);
        });
    }
}

class ControlFrame extends JFrame {
    private Socket socket;
    private JButton btnPlayAll, btnPlaySingle, btnPause, btnStop, btnDelete;
    
    public ControlFrame(Socket socket) {
        this.socket = socket;
        initUI();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }
    
    private void initUI() {
        setTitle("广播控制系统");
        setSize(350, 250);
        setLayout(new GridLayout(5, 1, 10, 10));
        
        btnPlayAll = new JButton("播放全部文件");
        btnPlaySingle = new JButton("播放默认文件");
        btnPause = new JButton("暂停");
        btnStop = new JButton("停止");
        btnDelete = new JButton("删除系统");
        
        add(btnPlayAll);
        add(btnPlaySingle);
        add(btnPause);
        add(btnStop);
        add(btnDelete);
        
        // 按钮事件监听
        btnPlayAll.addActionListener(e -> sendCommand("PLAY"));
        btnPlaySingle.addActionListener(e -> sendCommand("PLAY:default.mp3"));
        btnPause.addActionListener(e -> sendCommand("PAUSE"));
        btnStop.addActionListener(e -> sendCommand("STOP"));
        
        btnDelete.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                this, 
                "确定要完全删除系统吗？此操作不可逆！",
                "警告",
                JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                sendCommand("SHUTDOWN");
                selfDestruct();
                System.exit(0);
            }
        });
    }
    
    private void sendCommand(String cmd) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(cmd);
            
            // 简单状态反馈
            JOptionPane.showMessageDialog(this, "命令已发送: " + cmd);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "命令发送失败: " + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void selfDestruct() {
        try {
            // 获取当前JAR路径
            String jarPath = new File(
                BroadcastClient.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()
            ).getPath();
            
            // 创建卸载脚本
            File bat = new File("uninstall.bat");
            try (PrintWriter pw = new PrintWriter(bat)) {
                pw.println("@echo off");
                pw.println("echo 正在卸载广播系统...");
                pw.println("timeout /t 3 /nobreak > NUL");
                
                // 删除JAR文件
                pw.println("del \"" + jarPath + "\"");
                
                // 删除配置文件
                pw.println("del \"server.cfg\"");
                
                // 清理注册表
                pw.println("reg delete HKCU\\Software\\BroadcastSystem /f");
                
                // 自删除脚本
                pw.println("del \"%~f0\"");
            }
            
            // 执行卸载脚本
            Runtime.getRuntime().exec("cmd /c start uninstall.bat");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}