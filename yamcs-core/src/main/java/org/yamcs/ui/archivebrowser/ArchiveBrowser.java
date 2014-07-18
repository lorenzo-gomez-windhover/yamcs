package org.yamcs.ui.archivebrowser;

import org.yamcs.ConfigurationException;
import org.yamcs.TimeInterval;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsConnectDialog;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.ui.CommandHistoryRetrievalGui;
import org.yamcs.ui.PacketRetrievalGui;
import org.yamcs.ui.ParameterRetrievalGui;
import org.yamcs.ui.YamcsArchiveIndexReceiver;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * Standalone Archive Browser (useful for browsing the index, retrieving telemetry packets and parameters).
 * @author nm
 *
 */
public class ArchiveBrowser extends JFrame implements ArchiveIndexListener, ConnectionListener, ActionListener {
    private static final long serialVersionUID = 1L;
    ArchiveIndexReceiver indexReceiver;
    public ArchivePanel archivePanel;
    static boolean showDownload = false;
    JMenuItem packetRetrieval;
    JMenuItem parameterRetrieval;
    JMenuItem cmdHistRetrieval;
    PacketRetrievalGui packetGui;
    CommandHistoryRetrievalGui cmdHistGui;
    ParameterRetrievalGui parameterGui;
    JMenuItem connectMenuItem;
    
    YamcsConnector yconnector;
    
    private long lastErrorDialogTime = 0;
    
    public ArchiveBrowser(YamcsConnector yconnector, ArchiveIndexReceiver ir, boolean replayEnabled) throws IOException, ConfigurationException {
        super("Archive Browser");
        this.yconnector=yconnector;
        this.indexReceiver = ir;
        
        setIconImage(ArchivePanel.getIcon("yamcs-32x32.png").getImage());
        
        /*
         * MENU
         */
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        // File menu
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        connectMenuItem=new JMenuItem();
        connectMenuItem.setText("Connect to Yamcs...");
        connectMenuItem.setMnemonic(KeyEvent.VK_C);
        connectMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        fileMenu.add(connectMenuItem);

        fileMenu.addSeparator();

        JMenuItem closeMenuItem=new JMenuItem();
        closeMenuItem.setMnemonic(KeyEvent.VK_Q);
        closeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        closeMenuItem.setText("Quit");
        closeMenuItem.setActionCommand("exit");
        closeMenuItem.addActionListener(this);
        fileMenu.add(closeMenuItem);
        
        // Export menu
        JMenu selectionMenu = new JMenu("Selection");
        menuBar.add(selectionMenu);
        
        packetRetrieval = new JMenuItem("Export Packets...");
        packetRetrieval.setEnabled(false);
        packetRetrieval.setToolTipText("Start packet retrieval of the selected packets");
        packetRetrieval.addActionListener(this);
        packetRetrieval.setActionCommand("start-packet-retrieval");
        selectionMenu.add(packetRetrieval);
        
        parameterRetrieval = new JMenuItem("Export Parameters...");
        parameterRetrieval.setEnabled(false);
        parameterRetrieval.setToolTipText("Start parameter retrieval for the selected time interval");
        parameterRetrieval.addActionListener(this);
        parameterRetrieval.setActionCommand("start-parameter-retrieval");
        selectionMenu.add(parameterRetrieval);
        
        cmdHistRetrieval = new JMenuItem("Export Command History...");
        cmdHistRetrieval.setEnabled(false);
        cmdHistRetrieval.setToolTipText("Start command history retrieval for the selected time interval");
        cmdHistRetrieval.addActionListener(this);
        cmdHistRetrieval.setActionCommand("start-cmdhist-retrieval");
        selectionMenu.add(cmdHistRetrieval);

        menuBar.add(getToolsMenu());
   
        /*
         * BUTTONS
         */
        archivePanel = new ArchivePanel(this, replayEnabled);
        archivePanel.prefs.reloadButton.addActionListener(this);
        archivePanel.addActionListener(this);

        if(ir!=null && ir.supportsTags()) {
            archivePanel.enableNewTagButton();
        }

        // While resizing, only update active dataviewer (slight performance gain)
        // When done resizing, update all
        getRootPane().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                archivePanel.refreshActiveDataViewer();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                archivePanel.refreshAllDataViewers();
            }
        });
        
        setContentPane(archivePanel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
    }



    protected JMenu getToolsMenu() throws ConfigurationException, IOException {
        YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
        List<Map<String, String>> tools=config.getList("archiveBrowserTools");
        
        JMenu toolsMenu = new JMenu("Tools");
       

        for(Map<String, String> m:tools) {
            JMenuItem menuItem = new JMenuItem(m.get("name"));
            toolsMenu.add(menuItem);
            String className = m.get("class");
            YObjectLoader<JFrame> objLoader=new YObjectLoader<JFrame>();
            final JFrame f = objLoader.loadObject(className, yconnector);
            menuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    f.setVisible(true);
                }
            });
        }
        return toolsMenu;
    }
    
    
    protected void showMessage(String msg) {
        JOptionPane.showMessageDialog(this, msg, getTitle(), JOptionPane.PLAIN_MESSAGE);
    }
    
    protected void showInfo(String msg) {
        JOptionPane.showMessageDialog(this, msg, getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }
    
    protected void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, getTitle(), JOptionPane.ERROR_MESSAGE);
    }


    @Override
    public void connecting(String url) {
        log("Connecting to "+url);
    }

    @Override
    public void connected(String url) {
        try {
            List<String> instances = yconnector.getYamcsInstances();
            if(instances!=null) {
            	archivePanel.setInstances(instances);
            	archivePanel.connected();
            	requestData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        archivePanel.disconnected();
        // Display a helpful dialog, but prevent multiple dialogs if all
        // connections are failing.
        if( lastErrorDialogTime < (System.currentTimeMillis() - 5000) ) {
        	JOptionPane.showMessageDialog( this, exception.toString(), getClass().getName(), JOptionPane.ERROR_MESSAGE);
        	lastErrorDialogTime = System.currentTimeMillis();
        }
    }

    @Override
    public void disconnected() {
        archivePanel.disconnected();
    }

    @Override
    public void log(String text) {
        System.out.println(text);
    }

    public void popup(String text) {
        showMessage(text);
    }
    
    @Override
    public void receiveArchiveRecords(IndexResult ir) {
        archivePanel.receiveArchiveRecords(ir);
    }
    
	
    @Override
    public void receiveArchiveRecordsError(String errorMessage) {
        archivePanel.receiveArchiveRecordsError(errorMessage);
    }

    
    @Override
    public void receiveArchiveRecordsFinished() {
        if(indexReceiver.supportsTags()) {
            String instance=archivePanel.getInstance();
            TimeInterval interval = archivePanel.getRequestedDataInterval();
            indexReceiver.getTag(instance, interval);
        } else {
            archivePanel.dataLoadFinished();
        }
       /* if (archivePanel.histoBox.tmData.isEmpty()) {
            if(isVisible())
                JOptionPane.showMessageDialog(this, "No index data available for the selected interval");
        }*/
    }

    @Override
    public void receiveTagsFinished() {
        archivePanel.dataLoadFinished();
    }
    
    @Override
    public void actionPerformed(ActionEvent ae) {
        final String cmd = ae.getActionCommand();
        if(cmd.equalsIgnoreCase("reload")) {
            requestData();
        }  else if (cmd.equals("hide_resp")) {
            //  buildPopup();
        } else if (cmd.equals("show-dass-arc")) {
            /*
            Selection s = tmBox.getSelection();
            if (s != null) {
                String text = "Start_Archive_Replay Start_Time:\""
                    + format_yyyymmdd.format(s.getStartInstant()) + "\", End_Time:\""
                    + format_yyyymmdd.format(s.getStopInstant()) + "\"";
                showClipboardDialog(text);
            }
             */
        } else if (cmd.equals("show-raw-packet-dump")) {
            /*
            Selection s = tmBox.getSelection();
            if (s != null) {
                ArrayList<String> packets = new ArrayList<String>();
                for (ArrayList<TMTypeSpec> plvec:payloads.values()) {
                    for (TMTypeSpec pkt:plvec) {
                        if ( pkt.enabled ) {
                            packets.add(pkt.opsname);
                        }
                    }
                }
                StringBuffer sb=new StringBuffer();
                sb.append("hrdp-raw-packet-dump.sh ").append(prefs.getInstance()).append(" ");
                sb.append(TimeEncoding.toOrdinalDateTime(s.getStartInstant()));
                sb.append(" ");
                sb.append(TimeEncoding.toOrdinalDateTime(s.getStopInstant()));
                sb.append(" \"");
                for(String p:packets) sb.append(p).append(" ");
                sb.deleteCharAt(sb.length()-1);
                sb.append("\"");
                showClipboardDialog(sb.toString());
            }
             */
        }  else if (cmd.equals("start-packet-retrieval")) {
            List<String> packets=archivePanel.getSelectedPackets("tm");
            Selection sel = archivePanel.getSelection();
            if(packetGui==null) {
                packetGui=new PacketRetrievalGui(yconnector.getConnectionParams(), this);
            }
            packetGui.setValues(archivePanel.getInstance(), packets, sel.getStartInstant(), sel.getStopInstant());
            packetGui.setVisible(true);
       
        } else if (cmd.equals("start-parameter-retrieval")) {
            Selection sel = archivePanel.getSelection();
            if(parameterGui==null) {
                parameterGui=new ParameterRetrievalGui(yconnector.getConnectionParams(), this);
            }
            parameterGui.setValues(archivePanel.getInstance(), sel.getStartInstant(), sel.getStopInstant());
            parameterGui.setVisible(true);
        } else if (cmd.equals("start-cmdhist-retrieval")) {

            Selection sel = archivePanel.getSelection();
            if (cmdHistGui == null) {
                cmdHistGui = new CommandHistoryRetrievalGui(yconnector.getConnectionParams(), this);
            }
            cmdHistGui.setValues(archivePanel.getInstance(), null, sel.getStartInstant(), sel.getStopInstant());
            cmdHistGui.setVisible(true);
        } else if(cmd.equalsIgnoreCase("exit")) {
            System.exit(0);
        }
        
    }


    private void requestData() {
        //debugLog("requestData() mark 1 "+new Date());
        archivePanel.startReloading();
        TimeInterval interval = archivePanel.getRequestedDataInterval();
        String instance=archivePanel.getInstance();
        indexReceiver.getIndex(instance, interval);
    }



    private static void printUsageAndExit() {
        System.err.println("Usage: archive-browser.sh [-h] [-url url]");
        System.err.println("-h:\tShow this help text");
        System.err.println("-url:\tConnect at startup to the given url");
        System.err.println("Example to yamcs archive:\n\t archive-browser.sh -url yamcs://localhost:5445/");
        System.exit(1);
    }

    
    @Override
	public void receiveTags(final List<ArchiveTag> tagList) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
			    archivePanel.tagsAdded(tagList);
			}
		});
	}

    @Override
    public void tagAdded(final ArchiveTag ntag) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                archivePanel.tagAdded(ntag);
            }
        });
    }


    @Override
    public void tagRemoved(final ArchiveTag rtag) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                archivePanel.tagRemoved(rtag);
            }
        });
    }

    @Override
    public void tagChanged(final ArchiveTag oldTag, final ArchiveTag newTag) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                archivePanel.tagChanged(oldTag, newTag);
            }
        });
        
    }

    public static void main(String[] args) throws URISyntaxException, ConfigurationException, IOException {
        String initialUrl = null;
        YamcsConnectData params=null;
        for(int i=0;i<args.length;i++) {
            if(args[i].equals("-h")) {
                printUsageAndExit();
            } else {
                if(args.length!=i+1) printUsageAndExit();
                initialUrl=args[i];
                if(initialUrl.startsWith("yamcs://")){
                    params=YamcsConnectData.parse(initialUrl);
                 } else {
                     printUsageAndExit();
                 }
             }
        }
        TimeEncoding.setUp();
        final YamcsConnector yconnector=new YamcsConnector();
        final ArchiveIndexReceiver ir=new YamcsArchiveIndexReceiver(yconnector);
        final ArchiveBrowser archiveBrowser = new ArchiveBrowser(yconnector, ir, false);
        YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
        
        boolean aetmp = false;
        
        if(config.containsKey("authenticationEnabled")) {
            aetmp = config.getBoolean("authenticationEnabled");
        }
        
        final boolean enableAuth = aetmp;
        archiveBrowser.connectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                YamcsConnectData ycd=YamcsConnectDialog.showDialog(archiveBrowser, false, enableAuth);
                if(ycd.isOk) {
                    yconnector.connect(ycd);
                }
            }
        });
           
        ir.setIndexListener(archiveBrowser);
        yconnector.addConnectionListener(archiveBrowser);
        if(params!=null) {
            yconnector.connect(params);
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("shutting down");
                if(yconnector!=null) yconnector.disconnect();
            }
        });
        
        archiveBrowser.setVisible(true);
    }
}