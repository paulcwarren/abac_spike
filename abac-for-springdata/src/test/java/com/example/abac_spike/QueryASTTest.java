package com.example.abac_spike;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Describe;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.runner.RunWith;

import com.example.abac_spike.QueryAugmentingABACAspect.QueryAST;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;

@RunWith(Ginkgo4jRunner.class)
public class QueryASTTest {

    {
        Describe("QueryAST", () -> {

            It("should parse a query with a alias and where", () -> {
                QueryAugmentingABACAspect.QueryAST ast = QueryAST.fromQueryString("select d from AccountState d where d.type = :type");

                assertThat(ast.getQuery(), is("select"));
                assertThat(ast.getAttrs(), is("d"));
                assertThat(ast.getType(), is("AccountState"));
                assertThat(ast.getAlias(), is("d"));
                assertThat(ast.getWhere(), is("d.type = :type"));
                assertThat(ast.getOrderBy(), is(nullValue()));

                assertThat(ast.toString(), is("select d from AccountState d where d.type = :type"));
            });

            It("should parse a query with a count", () -> {
                QueryAugmentingABACAspect.QueryAST ast = QueryAST.fromQueryString("select count(entity) from AccountState entity");

                assertThat(ast.getQuery(), is("select"));
                assertThat(ast.getAttrs(), is("count(entity)"));
                assertThat(ast.getType(), is("AccountState"));
                assertThat(ast.getAlias(), is("entity"));
                assertThat(ast.getWhere(), is(nullValue()));
                assertThat(ast.getOrderBy(), is(nullValue()));

                assertThat(ast.toString(), is("select count(entity) from AccountState entity"));
            });

            It("should parse a query with an order by", () -> {
                QueryAugmentingABACAspect.QueryAST ast = QueryAST.fromQueryString("select count(entity) from AccountState entity order by entity.id desc");

                assertThat(ast.getQuery(), is("select"));
                assertThat(ast.getAttrs(), is("count(entity)"));
                assertThat(ast.getType(), is("AccountState"));
                assertThat(ast.getAlias(), is("entity"));
                assertThat(ast.getWhere(), is(nullValue()));
                assertThat(ast.getOrderBy(), is("entity.id desc"));

                assertThat(ast.toString(), is("select count(entity) from AccountState entity order by entity.id desc"));
            });

            It("should parse a query with a where and an order by", () -> {
                QueryAugmentingABACAspect.QueryAST ast = QueryAST.fromQueryString("select count(entity) from AccountState entity where entity.type = :type order by entity.id desc");

                assertThat(ast.getQuery(), is("select"));
                assertThat(ast.getAttrs(), is("count(entity)"));
                assertThat(ast.getType(), is("AccountState"));
                assertThat(ast.getAlias(), is("entity"));
                assertThat(ast.getWhere(), is("entity.type = :type"));
                assertThat(ast.getOrderBy(), is("entity.id desc"));

                assertThat(ast.toString(), is("select count(entity) from AccountState entity where entity.type = :type order by entity.id desc"));
            });

            It("should parse a query with carriage returns", () -> {
                QueryAugmentingABACAspect.QueryAST ast = QueryAST.fromQueryString("select entity\nfrom AccountState entity\nwhere entity.id = ?1");

                assertThat(ast.getQuery(), is("select"));
                assertThat(ast.getAttrs(), is("entity"));
                assertThat(ast.getType(), is("AccountState"));
                assertThat(ast.getAlias(), is("entity"));
                assertThat(ast.getWhere(), is("entity.id = ?1"));
                assertThat(ast.getOrderBy(), is(nullValue()));

                assertThat(ast.toString(), is("select entity from AccountState entity where entity.id = ?1"));
            });
        });
    }
}
