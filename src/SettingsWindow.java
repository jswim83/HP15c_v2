import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.ObjectInputStream;

public class SettingsWindow extends JFrame {

    private HP15c target;
    private JComboBox<String> instancePicker;

    public SettingsWindow(HP15c target) {
        this.target = target;
        this.instancePicker = new JComboBox<>();
        this.setUpWindow();
        this.setSize(600, 400);
        this.setTitle("Settings");
        this.validate();
        this.repaint();
    }

    private void setUpWindow(){
        var panel = new JPanel(new GridLayout(2, 3));

        panel.add(instancePicker);
        var loadButton = new JButton("Load Saved State");
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(instancePicker.getItemAt(0) != null) {
                    try {
                        var in = new ObjectInputStream(new FileInputStream(instancePicker.getItemAt(0)));
                        target.loadSettings((HP15cState) in.readObject());
                    }
                    catch (Exception exception){
                        System.out.println("error reading state");
                    }
                }
            }
        });
        panel.add(loadButton);
        panel.add(new JPanel());

        var saveAs = new JTextField("Save as: ");
        saveAs.setEditable(false);
        panel.add(saveAs);
        panel.add(new JTextField());
        var saveButton = new JButton("Save State");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ;
            }
        });


        this.add(panel);
    }
}
