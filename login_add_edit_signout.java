
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.Select;

public class LoginAddEditSignout {
  private WebDriver driver;
  private String baseUrl;
  private boolean acceptNextAlert = true;
  private StringBuffer verificationErrors = new StringBuffer();

  @Before
  public void setUp() throws Exception {
    driver = new FirefoxDriver();
    baseUrl = "http://localhost:8080/";
    driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
  }

  @Test
  public void testLoginAddEditSignout() throws Exception {
    driver.get(baseUrl + "/");
    driver.findElement(By.name("login")).clear();
    driver.findElement(By.name("login")).sendKeys("admin");
    driver.findElement(By.name("password")).clear();
    driver.findElement(By.name("password")).sendKeys("admin");
    driver.findElement(By.cssSelector("button.btn.btn-success")).click();
    // ERROR: Caught exception [ERROR: Unsupported command [getEval |  | ]]
    driver.findElement(By.linkText("Add article")).click();
    driver.findElement(By.id("title")).clear();
    driver.findElement(By.id("title")).sendKeys("MDMS-Article-" + artno);
    driver.findElement(By.cssSelector("div > textarea")).clear();
    driver.findElement(By.cssSelector("div > textarea")).sendKeys("O ephemeral text! Here today, gone tomorrow. Not terribly important, but necessary");
    driver.findElement(By.id("save")).click();
    assertEquals("MDMS-Article-" + artno + " Delete Edit", driver.findElement(By.xpath("(//h2[contains(text()," + artno + ")])")).getText());
    driver.findElement(By.xpath("(//h2[contains(text()," + artno + ")]/a[text()='Edit'])")).click();
    driver.findElement(By.cssSelector("div > textarea")).clear();
    driver.findElement(By.cssSelector("div > textarea")).sendKeys("And now the editor edits. Sigh. Everything is impermanent.");
    driver.findElement(By.id("save")).click();
    assertEquals("MDMS-Article-" + artno + " Delete Edit", driver.findElement(By.xpath("(//h2[contains(text()," + artno + ")])")).getText());
    driver.findElement(By.linkText("Sign out")).click();
  }

  @After
  public void tearDown() throws Exception {
    driver.quit();
    String verificationErrorString = verificationErrors.toString();
    if (!"".equals(verificationErrorString)) {
      fail(verificationErrorString);
    }
  }

  private boolean isElementPresent(By by) {
    try {
      driver.findElement(by);
      return true;
    } catch (NoSuchElementException e) {
      return false;
    }
  }

  private boolean isAlertPresent() {
    try {
      driver.switchTo().alert();
      return true;
    } catch (NoAlertPresentException e) {
      return false;
    }
  }

  private String closeAlertAndGetItsText() {
    try {
      Alert alert = driver.switchTo().alert();
      String alertText = alert.getText();
      if (acceptNextAlert) {
        alert.accept();
      } else {
        alert.dismiss();
      }
      return alertText;
    } finally {
      acceptNextAlert = true;
    }
  }
}
