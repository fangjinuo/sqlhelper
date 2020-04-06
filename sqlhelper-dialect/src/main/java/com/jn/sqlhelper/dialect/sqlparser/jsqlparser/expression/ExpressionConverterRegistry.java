package com.jn.sqlhelper.dialect.sqlparser.jsqlparser.expression;

import com.jn.langx.annotation.Singleton;
import com.jn.langx.util.Preconditions;
import com.jn.sqlhelper.dialect.expression.SQLExpression;
import net.sf.jsqlparser.expression.Expression;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class ExpressionConverterRegistry {
    private static final ExpressionConverterRegistry instance = new ExpressionConverterRegistry();

    private Map<Class<? extends SQLExpression>, ExpressionConverter> standardExpressionConverterMap = new HashMap<Class<? extends SQLExpression>, ExpressionConverter>();
    private Map<Class<? extends Expression>, ExpressionConverter> jsqlparserExpressionConverterMap = new HashMap<Class<? extends Expression>, ExpressionConverter>();

    private ExpressionConverterRegistry() {
    }

    public static ExpressionConverterRegistry getInstance() {
        return instance;
    }

    public void registry(ExpressionConverter converter) {
        Class<? extends SQLExpression> standardExpressionClass = converter.getStandardExpressionClass();
        Class<? extends Expression> jSqlParserExpressionClass = converter.getJSqlParserExpressionClass();
        Preconditions.checkNotNull(standardExpressionClass);
        Preconditions.checkNotNull(jSqlParserExpressionClass);
        standardExpressionConverterMap.put(standardExpressionClass, converter);
        jsqlparserExpressionConverterMap.put(jSqlParserExpressionClass, converter);
    }

}
