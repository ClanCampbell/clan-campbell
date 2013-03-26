package avi.copy;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Main {

	private static final class Copier extends Thread {

		private static boolean DEBUG = false;

		private static final int S_ABORTED = 0;

		private static final int S_COPYING = 1;

		private static final int S_PAUSED = 2;

		private volatile long bytesCopied;

		private final File destination;

		private final long length;

		private final File source;

		private volatile int state;

		private String trouble;

		public Copier(File source, File destination) {
			super("copy");
			this.bytesCopied = 0;
			this.destination = destination;
			this.length = source.length();
			this.source = source;
			this.state = S_COPYING;
			this.trouble = null;
		}

		public synchronized void abort() {
			state = S_ABORTED;
			notifyAll();
		}

		public synchronized long bytesCopied() {
			return bytesCopied;
		}

		public synchronized boolean done() {
			return bytesCopied == length;
		}

		public synchronized void pause() {
			if (state != S_ABORTED) {
				state = S_PAUSED;
				notifyAll();
			}
		}

		@Override
		public void run() {
			InputStream in = null;
			RandomAccessFile out = null;

			try {
				bytesCopied = 0;
				trouble = null;

				if (destination.exists()) {
					trouble = "Destination file exists.";
					return;
				}

				if (DEBUG) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// ignore
					}
					return;
				}

				destination.getParentFile().mkdirs();

				byte[] buffer;
				int len;

				out = new RandomAccessFile(destination, "rw");
				in = new FileInputStream(source);
				out.setLength(length);
				buffer = new byte[0x4000];

				for (;;) {
					if (waitUnpaused() == S_ABORTED) {
						break;
					}

					len = in.read(buffer);

					if (len < 0) {
						break;
					}

					if (waitUnpaused() == S_ABORTED) {
						break;
					}

					out.write(buffer, 0, len);
					bytesCopied += len;
				}
			} catch (IOException e) {
				trouble = e.getLocalizedMessage();
			} finally {
				safeClose(in);
				if (out != null && state == S_ABORTED) {
					try {
						out.setLength(0);
					} catch (IOException e) {
						// ignore - we're about to delete the file
					}
				}
				safeClose(out);
				bytesCopied = length;

				if (state != S_ABORTED) {
					destination.setLastModified(source.lastModified());
				} else {
					if (destination.exists()) {
						destination.delete();
					}
					if (trouble == null) {
						trouble = "aborted";
					}
				}
			}
		}

		public synchronized void unpause() {
			if (state == S_PAUSED) {
				state = S_COPYING;
				notifyAll();
			}
		}

		private synchronized int waitUnpaused() {
			while (state == S_PAUSED) {
				try {
					wait();
				} catch (InterruptedException e) {
					// ignore
				}
			}

			return state;
		}
	}

	private static final class WorkItem implements Comparable<WorkItem> {

		private final long length;

		private final long modified;

		private final String pathName;

		public WorkItem(String pathName, File file) {
			super();
			this.length = file.length();
			this.modified = file.lastModified();
			this.pathName = pathName;
		}

		@Override
		public int compareTo(WorkItem that) {
			return this.pathName.compareTo(that.pathName);
		}

		public long getLength() {
			return length;
		}

		public long getModified() {
			return modified;
		}

		public String getPathName() {
			return pathName;
		}

		@Override
		public String toString() {
			return String.format("%1$s (%2$,d bytes)", // <br/>
					pathName, Long.valueOf(length));
		}
	}

	private static final DateFormat DATE = new SimpleDateFormat("yyyyMMddHHmm");

	private static final Pattern Extensions = Pattern.compile(".*\\.(avi|mkv|mov|mp4|mpg)", Pattern.CASE_INSENSITIVE);

	public static void main(String[] args) {
		new Main().run(args);
	}

	private static Button newButton(Composite parent, String text) {
		Button button;

		button = new Button(parent, SWT.PUSH);
		button.setLayoutData(new GridData(SWT.LEAD, SWT.UP, false, false));
		button.setText(text);

		return button;
	}

	private static Label newLabel(Composite parent, String text) {
		Label label;

		label = new Label(parent, 16384);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		label.setText(text);

		return label;
	}

	private static ProgressBar newProgressBar(Composite parent) {
		ProgressBar bar;

		bar = new ProgressBar(parent, 65792);
		bar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		return bar;
	}

	private static Text newText(Composite parent) {
		Text field;

		field = new Text(parent, 18432);
		field.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));

		return field;
	}

	/*private*/static void safeClose(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private Document controlData;

	private Text controlFile;

	private Copier copier;

	private Button copyButton;

	private Text destinationFolder;

	private boolean dirty;

	private Button exitButton;

	private final Map<String, Date> newest;

	private Button pauseButton;

	private boolean paused;

	private ProgressBar progressBar;

	private Composite progressGroup;

	private Shell shell;

	private Button skipButton;

	private Text sourceFolder;

	private Label statusLabel;

	private int workIndex;

	private final List<WorkItem> workToDo;

	private Main() {
		super();
		this.dirty = false;
		this.newest = new TreeMap<>();
		this.workToDo = new ArrayList<>();
	}

	private void armUpdater(Runnable updater) {
		if (updater == null) {
			updater = new Runnable() {
				@Override
				public void run() {
					updateUI(this);
				}
			};
		}

		shell.getDisplay().timerExec(100, updater);
	}

	private String computeWork() {
		File srcDir = new File(sourceFolder.getText());

		if (!srcDir.exists() || !srcDir.isDirectory()) {
			return "Source folder not found.";
		}

		File ctlFile = new File(controlFile.getText());

		if (!ctlFile.exists() || ctlFile.isDirectory()) {
			return "Control file not found.";
		}

		File dstDir = new File(destinationFolder.getText());

		if (!dstDir.exists() || !dstDir.isDirectory()) {
			return "Destination folder not found.";
		}

		try {
			readControlData(ctlFile);
		} catch (IOException e) {
			return "Can't read control file: " + e.getMessage();
		}

		dirty = false;
		workToDo.clear();

		for (Entry<String, Date> entry : newest.entrySet()) {
			String folderName = entry.getKey();
			Date time = entry.getValue();
			File srcFolder = new File(srcDir, folderName);
			File dstFolder = new File(dstDir, folderName);

			String[] srcList = srcFolder.list();

			if (srcList == null) {
				continue;
			}

			for (String name : srcList) {
				if (!Extensions.matcher(name).matches()) {
					continue;
				}

				if (new File(dstFolder, name).exists()) {
					continue;
				}

				File file = new File(srcFolder, name);
				Date modified = new Date(file.lastModified());

				if (modified.after(time)) {
					String pathName = folderName + '/' + name;

					workToDo.add(new WorkItem(pathName, file));
				}
			}
		}

		Collections.sort(workToDo);

		return null;
	}

	/*private*/void copyPressed() {
		boolean wasPaused = paused;

		copyButton.setEnabled(false);
		exitButton.setEnabled(false);
		pauseButton.setEnabled(true);
		paused = false;
		progressGroup.setVisible(true);
		skipButton.setEnabled(true);

		if (copier != null) {
			copier.unpause();
		} else {
			if (!wasPaused) {
				workIndex = 0;
			}

			startNextFile();
			armUpdater(null);
		}
	}

	private void createControls(Composite parent) {
		parent.setLayout(new GridLayout());

		{
			Group group;

			group = new Group(parent, SWT.NONE);
			group.setLayout(new GridLayout(2, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
			group.setText("Configuration");

			ModifyListener settingsListener = new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					settingsModified();
				}
			};

			newLabel(group, "Source:");
			sourceFolder = newText(group);
			sourceFolder.addModifyListener(settingsListener);

			newLabel(group, "Control file:");
			controlFile = newText(group);
			controlFile.addModifyListener(settingsListener);

			newLabel(group, "Destination:");
			destinationFolder = newText(group);
			destinationFolder.addModifyListener(settingsListener);
		}

		{
			Composite buttons;

			buttons = new Composite(parent, SWT.NONE);
			buttons.setLayout(new GridLayout(4, false));
			buttons.setLayoutData(new GridData(SWT.CENTER, SWT.UP, false, false));

			copyButton = newButton(buttons, "Copy");
			copyButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					copyPressed();
				}
			});
			copyButton.setEnabled(false);

			pauseButton = newButton(buttons, "Pause");
			pauseButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					pausePressed();
				}
			});
			pauseButton.setEnabled(false);

			skipButton = newButton(buttons, "Skip file");
			skipButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					skipPressed();
				}
			});
			skipButton.setEnabled(false);

			exitButton = newButton(buttons, "Exit");
			exitButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					exitPressed();
				}
			});
			exitButton.setEnabled(true);
		}

		{
			GridData gridData;
			Composite group;

			gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			gridData.minimumWidth = 600;

			group = new Composite(parent, SWT.NONE);
			group.setLayout(new GridLayout());
			group.setLayoutData(gridData);
			group.setVisible(false);

			progressBar = newProgressBar(group);
			progressBar.addListener(SWT.Resize, new Listener() {
				@Override
				public void handleEvent(Event event) {
					ProgressBar bar = (ProgressBar) event.widget;
					Point size = bar.getSize();

					bar.setMinimum(0);
					bar.setMaximum(size.x);
				}
			});

			progressGroup = group;
		}

		{
			Composite group = new Composite(parent, SWT.NONE);

			group.setLayout(new GridLayout());
			group.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));

			statusLabel = new Label(group, SWT.LEAD);
			statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		}
	}

	/*private*/void exitPressed() {
		shell.dispose();
	}

	/*private*/void handleDispose() {
		if (copier != null) {
			copier.abort();
		}
	}

	/*private*/void pausePressed() {
		copier.pause();
		copyButton.setEnabled(true);
		exitButton.setEnabled(false);
		pauseButton.setEnabled(false);
		paused = true;
		skipButton.setEnabled(true);
	}

	private void readControlData(File file) throws IOException {
		InputStream in = null;

		controlData = null;

		try {
			in = new FileInputStream(file);

			controlData = DocumentBuilderFactory // <br/>
					.newInstance() // <br/>
					.newDocumentBuilder() // <br/>
					.parse(in);
		} catch (ParserConfigurationException e) {
			throw new IOException(e);
		} catch (SAXException e) {
			throw new IOException(e);
		} finally {
			safeClose(in);
		}

		if (controlData == null) {
			return;
		}

		newest.clear();

		NodeList children = controlData.getDocumentElement().getChildNodes();

		for (int i = 0, n = children.getLength(); i < n; ++i) {
			Node node = children.item(i);

			if (!(node instanceof Element)) {
				continue;
			}

			Date date;
			Element element = (Element) node;
			Attr time;
			Attr title;

			if (!"video".equals(element.getTagName())) {
				continue;
			}

			if ((title = element.getAttributeNode("title")) == null) {
				continue;
			}

			if ((time = element.getAttributeNode("time")) == null) {
				date = new Date(0);
			} else {
				try {
					date = DATE.parse(time.getValue());
				} catch (ParseException e) {
					date = new Date(0);
				}
			}

			newest.put(title.getValue(), date);
		}
	}

	private void run(String[] args) {
		Display display = new Display();

		shell = new Shell(display);

		createControls(shell);

		controlFile.setText(args.length > 0 ? args[0] : "D:/video/Meghan.xml");
		destinationFolder.setText(args.length > 1 ? args[1] : "E:/video");
		sourceFolder.setText(args.length > 2 ? args[2] : "D:/video");

		updateStatus();

		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				handleDispose();
			}
		});

		shell.setText("Video Copier");
		shell.pack();
		shell.setMinimumSize(shell.getSize());
		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}

		display.dispose();
		handleDispose();
	}

	private void saveControlData(File file) throws IOException {
		NodeList children = controlData.getDocumentElement().getChildNodes();

		for (int i = 0, n = children.getLength(); i < n; ++i) {
			Node node = children.item(i);

			if (!(node instanceof Element)) {
				continue;
			}

			Date date;
			Element element = (Element) node;
			Attr time;
			String timeValue;
			Attr title;

			if (!"video".equals(element.getTagName())) {
				continue;
			}

			if ((title = element.getAttributeNode("title")) == null) {
				continue;
			}

			if ((date = newest.get(title.getValue())) == null) {
				continue;
			}

			timeValue = DATE.format(date);

			if ((time = element.getAttributeNode("time")) != null) {
				time.setValue(timeValue);
			} else {
				element.setAttribute("time", timeValue);
			}
		}

		FileWriter writer = new FileWriter(file);

		try {
			DOMSource source;
			StreamResult target;
			Transformer transformer;

			transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty("encoding", "utf-8");
			transformer.setOutputProperty("indent", "yes");
			transformer.setOutputProperty("method", "xml");

			source = new DOMSource(controlData);

			target = new StreamResult(file);

			target.setWriter(writer);

			transformer.transform(source, target);
		} catch (TransformerException e) {
			throw new IOException(e);
		} finally {
			writer.close();
		}
	}

	/*private*/void settingsModified() {
		updateStatus();
	}

	/*private*/void skipPressed() {
		if (copier != null) {
			copier.abort();
		}
	}

	private void startNextFile() {
		if (workIndex < workToDo.size()) {
			WorkItem item = workToDo.get(workIndex);
			String pathName = item.getPathName();
			File source = new File(sourceFolder.getText(), pathName);
			File destination = new File(destinationFolder.getText(), pathName);

			copier = new Copier(source, destination);

			if (paused) {
				copier.pause();
			}

			copier.start();
		} else {
			copier = null;
			copyButton.setEnabled(true);
			exitButton.setEnabled(true);
			pauseButton.setEnabled(false);
			skipButton.setEnabled(false);
		}
	}

	private void updateNewest(WorkItem item) {
		String pathName = item.getPathName();
		int slash = pathName.lastIndexOf('/');

		if (slash >= 0) {
			String folderName = pathName.substring(0, slash);
			Date current = newest.get(folderName);
			Date date = new Date(item.getModified());

			if (current == null || date.after(current)) {
				dirty = true;
				newest.put(folderName, date);
			}
		}
	}

	private void updateStatus() {
		String error = computeWork();
		int fileCount = workToDo.size();
		String status;

		if (error != null) {
			copyButton.setEnabled(false);
			status = error;
		} else if (fileCount == 0) {
			copyButton.setEnabled(false);
			status = "Destination is up-to-date.";
		} else {
			long totalBytes = 0;

			for (WorkItem workItem : workToDo) {
				totalBytes += workItem.getLength();
			}

			status = String.format("%3$,d %4$s in %1$d %2$s to be copied.", // <br/>
					Integer.valueOf(fileCount), // <br/>
					(fileCount == 1 ? "file" : "files"), // <br/>
					Long.valueOf(totalBytes), // <br/>
					(totalBytes == 1 ? "byte" : "bytes"));

			copyButton.setEnabled(true);
		}

		statusLabel.setText(status);
	}

	/*private*/void updateUI(Runnable updater) {
		if (shell.isDisposed()) {
			return;
		}

		if (copier != null && copier.done()) {
			updateNewest(workToDo.get(workIndex));
			copier = null;
			workIndex += 1;

			if (!paused) {
				startNextFile();
			}
		}

		if (copier == null) {
			copyButton.setEnabled(true);
			exitButton.setEnabled(true);
			pauseButton.setEnabled(false);
			progressGroup.setVisible(paused);
			skipButton.setEnabled(false);

			if (dirty && !paused) {
				File ctlFile = new File(controlFile.getText());

				try {
					saveControlData(ctlFile);
					dirty = false;
					updateStatus();
				} catch (IOException e) {
					statusLabel.setText("Can't save control file: " + e.getLocalizedMessage());
				}
			}

			return;
		}

		long bytesCopied = 0;
		int fileCount = workToDo.size();
		long totalBytes = 0;

		for (int i = 0; i < fileCount; ++i) {
			WorkItem item = workToDo.get(i);
			long length = item.getLength();

			if (i < workIndex) {
				bytesCopied += length;
			} else if (i == workIndex) {
				bytesCopied += copier.bytesCopied();
				statusLabel.setText("Copying " + item);
			}

			totalBytes += length;
		}

		if (totalBytes <= 0) {
			totalBytes = 1;
		}

		int max = progressBar.getMaximum();
		int current = (int) ((bytesCopied / (double) totalBytes) * max);

		progressBar.setSelection(current);
		armUpdater(updater);
	}
}
