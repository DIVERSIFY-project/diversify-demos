package org.diversify.kevoree.sosie;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kevoree.tools.test.KevoreeTestCase;

import java.io.File;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 25/02/14
 * Time: 08:56
 *
 * @author Erwan Daubert
 * @version 1.0
 */
public class SosieRunnerTest extends KevoreeTestCase {

    @Before
    public void beforeTest() throws Exception {
        try {
            bootstrap("node0", "boot.kevs");
        } catch (Exception e) {
            Assert.fail("Unable to bootstrap the runtime: " + e.getMessage());
        }
    }

    @After
    public void afterTest() {

    }

    @Test
    public void testStart() throws Exception {
        try {
            exec("node0", "add node0.sosie1 : SosieRunner\n" +
                    "set node0.sosie1.sosieUrl = 'http://sd-35000.dedibox.fr:8080/archiva/repository/internal/org/diversify/composed-sosie/1-indirection_on_Streamrhino8/composed-sosie-1-indirection_on_Streamrhino8.zip'\n" +
                    "set node0.sosie1.port = '8282'\n" +
                    "set node0.sosie1.started = 'false'\n");
        } catch (Exception e) {
            Assert.fail("Unable to add the SosieRunner component");
        }

        try {
        exec("node0", "set node0.sosie1.started = 'true'");
        } catch (Exception e) {
            Assert.fail("Unable to start the SosieRunner component");
        }

        waitLog("node0", "^node0/[\\:/0-9]* INFO: Sosie 'sosie1' is started$", 5000);
        File directory = new File(System.getProperty("java.io.tmpdir") + File.separator + "sosie1");
        Assert.assertTrue(directory.exists());

        try {
            exec("node0", "set node0.sosie1.started = 'false'");
        } catch (Exception e) {
            Assert.fail("Unable to stop the SosieRunner component");
        }
        Assert.assertFalse(directory.exists());
    }

    @Test
    public void testStop() {

    }
}
