package com.plantssoil.common.jpa.impl;

import java.io.FileOutputStream;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.spi.NamingManager;

import org.apache.commons.dbcp.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.plantssoil.common.config.LettuceConfiguration;

public class DefaultPersistenceTest {
	private DefaultPersistenceCases test = new DefaultPersistenceCases();

	@Before
	public void setUp() throws Exception {
		Properties p = new Properties();
		p.setProperty(LettuceConfiguration.RDBMS_JPA_PERSISTENCE_CONFIGURABLE, DefaultPersistence.class.getName());
		p.setProperty(LettuceConfiguration.ENGINE_CORE_DATASOURCE, "java:com/env/test");

		BasicDataSource ds = new BasicDataSource();
		ds.setDriverClassName(org.h2.Driver.class.getName());
		ds.setUrl("jdbc:h2:mem:testShared;DB_CLOSE_DELAY=-1");
		ds.setUsername("sa");
		ds.setPassword("sa");

		InitialContext context = new InitialContext();
		System.setProperty(Context.INITIAL_CONTEXT_FACTORY, MockInitialContextFactory.class.getName());
		new NamingManager().setInitialContextFactoryBuilder();
		MockInitialContextFactory.setCurrentContext(context);
		context.bind("java:com/env/test", ds);

		try (FileOutputStream out = new FileOutputStream(test.util.getTempDir() + "/lettuce.properties")) {
			p.store(out, "## No comments");
		}

		System.setProperty("lettuce.config.dir", test.util.getTempDir());
	}

	@After
	public void tearDown() throws Exception {
		test.util.removeTempDirectory();
	}

	@Test
	public void testCreateObject() {
		test.testCreate();
	}

	@Test
	public void testCreateListOfQ() {
		test.testCreateList();
	}

	@Test
	public void testUpdateT() {
		test.testUpdate();
	}

	@Test
	public void testUpdateListOfT() {
		test.testUpdateList();
	}

	@Test
	public void testRemoveObject() {
		test.testRemove();
	}

	@Test
	public void testRemoveListOfQ() {
		test.testRemoveList();
	}
}