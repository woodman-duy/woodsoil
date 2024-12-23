package com.plantssoil.webhook.core;

import java.io.FileOutputStream;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.plantssoil.common.config.ConfigFactory;
import com.plantssoil.common.config.LettuceConfiguration;
import com.plantssoil.common.test.TempDirectoryUtility;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SingleSimpleMessageQueueEngineTest extends IEngineTestParent {
    private static TempDirectoryUtility util = new TempDirectoryUtility();

    public static void main(String[] args) throws Exception {
        SingleSimpleMessageQueueEngineTest.setUpBeforeClass();
        SingleSimpleMessageQueueEngineTest test = new SingleSimpleMessageQueueEngineTest();
        test.test01GetVersion();
        test.test02GetRegistry();
        test.test03Trigger();
        Thread.sleep(10000);
        SingleSimpleMessageQueueEngineTest.tearDownAfterClass();
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Thread.sleep(1000);
        Properties p = new Properties();
//        p.setProperty(LettuceConfiguration.PERSISTENCE_FACTORY_CONFIGURABLE, MongodbPersistenceFactory.class.getName());
//        p.setProperty(LettuceConfiguration.PERSISTENCE_DATABASE_URL,
//                "mongodb://lettuce:lettuce20241101@192.168.0.67:27017/?retryWrites=false&retryReads=false");
        p.setProperty(LettuceConfiguration.MESSAGE_SERVICE_FACTORY_CONFIGURABLE, com.plantssoil.common.mq.simple.MessageServiceFactory.class.getName());
        p.setProperty(LettuceConfiguration.WEBHOOK_ENGINE_VERSION, "1.0.0");
        p.setProperty(LettuceConfiguration.WEBHOOK_ENGINE_FACTORY_CONFIGURABLE,
                com.plantssoil.webhook.core.impl.SingleMessageQueueEngineFactory.class.getName());

        try (FileOutputStream out = new FileOutputStream(util.getTempDir() + "/" + LettuceConfiguration.CONFIGURATION_FILE_NAME)) {
            p.store(out, "## No comments");
        }
        System.setProperty(LettuceConfiguration.CONF_DIRECTORY_PROPERTY_NAME, util.getTempDir());
        ConfigFactory.reload();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        util.removeTempDirectory();
    }

    @Test
    public void test01GetVersion() {
        testGetVersion();
    }

    @Test
    public void test02GetRegistry() {
        testGetRegistry();
    }

    @Test
    public void test03Trigger() {
        testTrigger();
    }

}