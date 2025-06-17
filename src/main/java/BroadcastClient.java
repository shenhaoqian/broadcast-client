import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;

public class BroadcastClient {
    private static Socket socket;

    public static void main(String[] args) {
        // 尝试连接服务器
        if (!connectToServer()) {
            return;
        }
        
        // 显示控制界面
        SwingUtilities.invokeLater(() -> {
            ControlFrame frame = new ControlFrame(socket);
            frame.setVisible(true);
        });
    }

    private static boolean connectToServer() {
        String defaultAddress = "127.0.0.1:8899"; // 默认使用本地地址
        String serverAddress = (String) JOptionPane.showInputDialog(null, 
            "请输入服务器地址 (IP:端口)\n本机测试请使用127.0.0.1", 
            "广播系统连接", 
            JOptionPane.QUESTION_MESSAGE, null, null, defaultAddress);
        
        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            return false;
        }
        
        String[] parts = serverAddress.split(":");
        if (parts.length != 2) {
            JOptionPane.showMessageDialog(null, "地址格式错误！应为 IP:端口", "错误", 
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        try {
            String ip = parts[0].trim();
            int port = Integer.parseInt(parts[1].trim());
            
            // 设置连接超时时间
            Socket testSocket = new Socket();
            testSocket.connect(new InetSocketAddress(ip, port), 3000); // 3秒超时
            socket = testSocket;
            
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "连接失败: " + e.getMessage() + 
                "\n请检查:\n1. 服务端是否运行\n2. 防火墙设置\n3. IP地址是否正确", 
                "连接错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
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