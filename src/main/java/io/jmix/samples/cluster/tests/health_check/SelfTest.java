package io.jmix.samples.cluster.tests.health_check;

import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.*;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_selfTest")
@ClusterTest(cleanStart = true,
        description = "Checks that test system works")
public class SelfTest {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SelfTest.class);

    public static ThreadLocal<Boolean> beforeStepInvokedRecently = new ThreadLocal<>();
    public static boolean valueClearedByAfterStep = false;


    @BeforeTest
    public void doBefore(TestContext context) {
        log.info("@BeforeTest method invoked");
        context.put("initial", "exists");
        //throw new RuntimeException("@BeforeTest method failed by design");//enable to check @BeforeTest failing
    }

    @BeforeStep
    public void beforeStep(TestContext context) {
        beforeStepInvokedRecently.set(true);
    }

    @AfterStep
    public void afterStep(TestContext context) {
        valueClearedByAfterStep = true;
    }


    @Step(order = 1, nodes = "1")
    public boolean doStep1(TestContext context) {
        log.info("annotated test 1, step 1");
        assertThat(context.get("initial")).isEqualTo("exists");
        context.put("a", "b");
        return true;
    }

    @Step(order = 2, nodes = "2")
    public void doStep2(TestContext context) {
        assertThat(context.get("a")).isEqualTo("b");
        context.put("c", "d");

        log.info("annotated test 1, step 2");

        assertThat(beforeStepInvokedRecently.get()).isTrue();
        beforeStepInvokedRecently.set(false);
        valueClearedByAfterStep = false;
    }

    @Step(order = 3, nodes = {"1", "2"})
    public void doStep3() {
        log.info("annotated test 1, step 3");

        assertThat(beforeStepInvokedRecently.get()).isTrue();
        beforeStepInvokedRecently.set(false);
        assertThat(valueClearedByAfterStep).isTrue();
        valueClearedByAfterStep = false;
    }

    @Step(order = 4)
    public void doStep4(TestContext context) {
        assertThat(context.get("a")).isEqualTo("b");
        assertThat(context.get("c")).isEqualTo("d");

        log.info("annotated test 1, step 3 [check that it works without context]");
        assertThat(beforeStepInvokedRecently.get()).isTrue();
        beforeStepInvokedRecently.set(false);
        assertThat(valueClearedByAfterStep).isTrue();
        valueClearedByAfterStep = false;
    }

    //@Step(order = 5)//enable to check test failing
    public void doStep5() {
        throw new RuntimeException("Test failed by design");
    }

    @AfterTest
    public void doAfter() {
        log.info("@AfterTest method invoked");
        //throw new RuntimeException("@AfterTest method failed by design");//enable to check @AfterTest failing
    }
}
