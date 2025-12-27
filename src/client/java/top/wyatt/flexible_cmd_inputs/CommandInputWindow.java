package top.wyatt.flexible_cmd_inputs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

@SuppressWarnings("serial")
public class CommandInputWindow extends JFrame {
    private JTextField commandInput;

    // 静态方法：安全创建窗口（对外暴露的调用入口）
    public static void createAndShowWindow() {
        // 1. 检测是否为Headless模式，提前规避异常
        if (GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null,
                "当前环境不支持图形界面！请禁用Headless模式后重试。",
                "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. 确保在AWT事件调度线程（EDT）中创建窗口（Swing线程安全要求）
        SwingUtilities.invokeLater(() -> {
            new CommandInputWindow().setVisible(true);
        });
    }

    // 私有化构造方法，仅通过createAndShowWindow创建
    CommandInputWindow() {
        // 窗口核心配置
        setTitle("灵活指令输入 - F6窗口");
        setSize(400, 100);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null); // 窗口居中
        setResizable(false);

        // 关闭窗口时的清理逻辑
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose(); // 释放窗口资源
            }
        });

        // UI组件布局
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 带占位符的输入框
        commandInput = new PlaceholderTextField();
        ((PlaceholderTextField) commandInput).setPlaceholder("输入游戏指令（无需/）");
        panel.add(commandInput, BorderLayout.CENTER);

        // 执行按钮
        JButton sendBtn = new JButton("执行指令");
        sendBtn.addActionListener(this::onSendCommand);
        panel.add(sendBtn, BorderLayout.EAST);

        // 添加面板到窗口
        add(panel);
        // 确保组件加载完成后聚焦输入框
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                commandInput.requestFocusInWindow();
            }
        });
    }

    // 指令发送逻辑
    private void onSendCommand(ActionEvent e) {
        String command = commandInput.getText().trim();
        if (command.isBlank()) {
            JOptionPane.showMessageDialog(this, "指令不能为空！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 调用指令执行方法
        boolean success = FlexibleCommandInputs.executeCommandAndFeedback(command);
        if (success) {
            JOptionPane.showMessageDialog(this, "指令已提交：" + command, "成功", JOptionPane.INFORMATION_MESSAGE);
            commandInput.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "指令执行失败（玩家未加载/指令错误）", "失败", JOptionPane.ERROR_MESSAGE);
        }
        commandInput.requestFocus();
    }

    // 自定义占位符输入框
    private static class PlaceholderTextField extends JTextField {
        private String placeholder;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (placeholder != null && getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(Color.GRAY);
                g2.drawString(placeholder, getInsets().left, g.getFontMetrics().getAscent() + getInsets().top);
            }
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
            repaint();
        }
    }
}