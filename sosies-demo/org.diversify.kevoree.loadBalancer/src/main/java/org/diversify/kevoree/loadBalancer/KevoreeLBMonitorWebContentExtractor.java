package org.diversify.kevoree.loadBalancer;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 14/02/14
 * Time: 14:29
 *
 * @author Erwan Daubert
 * @version 1.0
 */
public class KevoreeLBMonitorWebContentExtractor {

    public void extractConfiguration(String folderPath, boolean deleteOnExit) throws IOException {
        unzip(KevoreeLBMonitorWebContentExtractor.class.getClassLoader().getResourceAsStream("webContent.zip"), folderPath, deleteOnExit);
    }

    public void unzip(InputStream zipStream, String destDirectory, boolean deleteOnExit) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(zipStream);
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath, deleteOnExit);
                if (deleteOnExit) {
                    new File(filePath).deleteOnExit();
                }
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdirs();
                if (deleteOnExit) {
                    dir.deleteOnExit();
                }
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }
    
    public void replaceFileString(String old, String _new, String destDirectory) throws IOException {
        String fileName = destDirectory + File.separator + "client"+ File.separator +"lbmonitor_client.html";
        FileInputStream fis = new FileInputStream(fileName);
        String content = IOUtils.toString(fis, Charset.defaultCharset());
        content = content.replaceAll(old, _new);
        FileOutputStream fos = new FileOutputStream(fileName);
        IOUtils.write(content, new FileOutputStream(fileName), Charset.defaultCharset());
        fis.close();
        fos.close();
    }
    
    
    private void extractFile(ZipInputStream zipIn, String filePath, boolean deleteOnExit) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[2048];
        int read;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    
    private KevoreeLBMonitorWebContentExtractor() {
	}
    
    private static KevoreeLBMonitorWebContentExtractor instance;
	public static KevoreeLBMonitorWebContentExtractor getInstance() {
		if (instance==null){
			instance = new KevoreeLBMonitorWebContentExtractor();
		}
		return instance;
	}
}
