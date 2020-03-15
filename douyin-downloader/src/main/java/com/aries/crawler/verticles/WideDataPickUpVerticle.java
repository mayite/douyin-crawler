package com.aries.crawler.verticles;

import com.aries.crawler.model.douyincrawler.DouyinCrawlerLogModel;
import com.aries.crawler.sqlbuilder.SelectBuilder;
import com.aries.crawler.tools.MySqlExecuteHelper;
import com.aries.crawler.tools.Orm;
import com.aries.crawler.trans.message.DouyinWideDataMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/**
 * 这个verticle的职责是：
 * 从宽表douyin_crawler_log中读取数据, 然后将数据派发给WideDataDispatchVerticle来做分派处理
 *
 * @author arowana
 */
public class WideDataPickUpVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(WideDataPickUpVerticle.class);
    private Supplier<Void> supplier = () -> {
        var sql = new SelectBuilder()
                .column("*")
                .from(DouyinCrawlerLogModel.TABLE)
                .where(" status != " + DouyinCrawlerLogModel.STATUS_ALL_DONE)
                .limit(100L)
                .orderBy("ct", false)
                .toString();

        MySqlExecuteHelper.prepareExecute(vertx, sql, new ArrayList<>(), mysqlExecutorRes -> {
            if (mysqlExecutorRes.succeeded()) {
                System.out.println("---====" + mysqlExecutorRes.result());
                List<JsonObject> rows = mysqlExecutorRes.result().getRows();

                for (JsonObject row : rows) {
                    var model = Orm.getModel(row, DouyinCrawlerLogModel.class);
                    processWideData(model);
                }
            } else {
                logger.error(mysqlExecutorRes.cause());
            }
        });

        return null;
    };

    @Override
    public void start() {
        supplier.get();
    }

    public void processWideData(DouyinCrawlerLogModel model) {
        if (model == null) {
            // do nothing
            return;
        }

        var douyinWideDataMessage = DouyinWideDataMessage.of(model);
        vertx.eventBus().request("logic.widedata.dispatch", douyinWideDataMessage, reply -> {
            if (reply.succeeded()) {
                logger.info("Received reply succeeded. id: " + douyinWideDataMessage.getId());
            } else {
                logger.error("Received reply failed. id: " + douyinWideDataMessage.getId());
            }
        });
    }


}