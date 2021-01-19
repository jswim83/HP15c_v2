import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;

public class SettingsWindow extends JFrame {

    private HP15c target;

    public SettingsWindow(HP15c target) {
        this.target = target;
        this.init();
        this.setSize(600, 400);
        this.setTitle("Settings");
        this.setVisible(true);
    }

    protected void init(){
        var centerPanel = new JPanel(new GridLayout(2, 1));

        var loadPanel = new JPanel();
        //loadPanel.add(instancePicker);
        var loadNameText = new JTextField();
        loadNameText.setPreferredSize(new Dimension(100, 40));
        loadPanel.add(loadNameText);
        var loadButton = new JButton("Load Saved State");
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //if(instancePicker.getItemAt(0) != null) {
                    try {
                        //var fileIn = new FileInputStream(instancePicker.getItemAt(0));
                        var fileIn = new FileInputStream(loadNameText.getText() + ".ser");
                        var objectIn = new ObjectInputStream(fileIn);
                        target.loadSettings((HP15cState) objectIn.readObject());
                        fileIn.close();
                        objectIn.close();
                    }
                    catch (Exception exception){
                        exception.printStackTrace();
                        //System.out.println("error loading state");
                    }
                //}
            }
        });
        loadPanel.add(loadButton);
        loadPanel.add(new JPanel());

        var savePanel = new JPanel();
        var saveAs = new JLabel("Save as: ");
        savePanel.add(saveAs);
        var saveNameText = new JTextField();
        saveNameText.setPreferredSize(new Dimension(100, 40));
        savePanel.add(saveNameText);
        var saveButton = new JButton("Save State");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    var fileOut = new FileOutputStream(saveNameText.getText() + ".ser");
                    var objectOut = new ObjectOutputStream(fileOut);
                    objectOut.writeObject(target.getHP15cState());
                    fileOut.close();
                    objectOut.close();
                }
                catch (Exception exception){
                    exception.printStackTrace();
                    System.out.println("error saving state");
                }
            }
        });
        savePanel.add(saveButton);

        centerPanel.add(loadPanel);
        centerPanel.add(savePanel);
        this.add(centerPanel);
    }
}
