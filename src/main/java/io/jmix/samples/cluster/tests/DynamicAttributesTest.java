package io.jmix.samples.cluster.tests;

import io.jmix.core.Metadata;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.data.entity.ReferenceToEntity;
import io.jmix.dynattr.AttributeDefinition;
import io.jmix.dynattr.AttributeType;
import io.jmix.dynattr.CategoryDefinition;
import io.jmix.dynattr.DynAttrMetadata;
import io.jmix.dynattr.model.Category;
import io.jmix.dynattr.model.CategoryAttribute;
import io.jmix.samples.cluster.entity.Sample;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_DynamicAttributesTest")
@ClusterTest(description = "Checks dynamic attributes cache works correctly in cluster")
public class DynamicAttributesTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicAttributesTest.class);

    @Autowired
    Metadata metadata;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private DynAttrMetadata dynAttrMetadata;

    @Autowired
    private DataSource dataSource;

    @Step(order = 0, nodes = "1", description = "Init dynamic attributes")
    public void initDynamicAttributes(TestContext context) {
        doClear();
        Category category = dataManager.create(Category.class);
        category.setName("sample");
        category.setEntityType("cluster_Sample");

        CategoryAttribute attribute = dataManager.create(CategoryAttribute.class);
        attribute.setName("sampleAttribute");
        attribute.setCode("sampleAttribute");
        attribute.setDataType(AttributeType.STRING);
        attribute.setCategoryEntityType("cluster_Sample");
        attribute.setCategory(category);
        attribute.setDefaultEntity(new ReferenceToEntity());

        dataManager.save(category, attribute);

        context.put("category", category);
        context.put("attribute", attribute);

    }

    @Step(order = 1, nodes = "2", description = "Check dynamic attributes loaded")
    public void loadDynamicAttributes(TestContext context) {

        Collection<AttributeDefinition> attributes = dynAttrMetadata.getAttributes(metadata.getClass(Sample.class));
        assertThat(attributes)
                .isNotEmpty()
                .hasSize(1);

        AttributeDefinition attributeDefinition = attributes.iterator().next();
        assertThat(attributeDefinition.getName()).isEqualTo("sampleAttribute");
        assertThat(attributeDefinition.getName()).isEqualTo("sampleAttribute");

        Collection<CategoryDefinition> categories = dynAttrMetadata.getCategories(metadata.getClass(Sample.class));

        assertThat(categories)
                .isNotEmpty()
                .hasSize(1);

        CategoryDefinition categoryDefinition = categories.iterator().next();

        assertThat(categoryDefinition.getName()).isEqualTo("sample");

    }

    @Step(order = 2, nodes = "1", description = "Update dynamic attributes")
    public void updateAttribute(TestContext context) {
        CategoryAttribute attribute = (CategoryAttribute) context.get("attribute");
        attribute.setName("updatedName");
        dataManager.save(attribute);
        dynAttrMetadata.reload();
    }

    @Step(order = 3, nodes = "2", description = "Check that cache refreshed for all nodes")
    public void makeSureCacheReset(TestContext context) {
        AttributeDefinition attributeDefinition = dynAttrMetadata.getAttributes(metadata.getClass(Sample.class)).iterator().next();
        assertThat(attributeDefinition.getName()).isEqualTo("updatedName");
    }


    @Step(order = 10, description = "cleanup")
    public void cleanTables(TestContext context) {
        doClear();//todo AfterAll mandatory method through annotation
    }

    protected void doClear() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from DYNAT_ATTR_VALUE");
        jdbcTemplate.update("delete from DYNAT_CATEGORY_ATTR");
        jdbcTemplate.update("delete from DYNAT_CATEGORY");
        dynAttrMetadata.reload();
    }
}
