package com.trib3.db.flyway

import org.easymock.EasyMock
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.testng.annotations.Test
import javax.sql.DataSource

class FlywayBundleTest {
    @Test
    fun testFlywayMigrate() {
        val mockConfig = EasyMock.mock<FluentConfiguration>(FluentConfiguration::class.java)
        val mockDatasource = EasyMock.mock<DataSource>(DataSource::class.java)
        val mockFlyway = EasyMock.mock<Flyway>(Flyway::class.java)
        EasyMock.expect(mockConfig.dataSource(mockDatasource)).andReturn(mockConfig).once()
        EasyMock.expect(mockConfig.load()).andReturn(mockFlyway).once()
        EasyMock.expect(mockFlyway.migrate()).andReturn(1).once()
        EasyMock.replay(mockConfig, mockDatasource, mockFlyway)
        val bundle = FlywayBundle(mockDatasource, mockConfig)
        bundle.run(null, null)
        EasyMock.verify(mockConfig, mockDatasource, mockFlyway)
    }
}
