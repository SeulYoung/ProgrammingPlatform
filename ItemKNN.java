package mine.mahout.practice;

import java.io.File;
import java.util.List;

import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.apache.mahout.cf.taste.impl.recommender.knn.KnnItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.recommender.knn.NonNegativeQuadraticOptimizer;


public class ItemKNN 
{
    @SuppressWarnings("deprecation")
	public static void main( String[] args )
    {
    	try{
    		DataModel model = new FileDataModel(new File("data.csv"));
            
            ItemSimilarity itemSimilarity = RecommendFactory.itemSimilarity(RecommendFactory.SIMILARITY.EUCLIDEAN, model);
            RecommenderBuilder recommenderBuilder = RecommendFactory.itemKNNRecommender(itemSimilarity, new NonNegativeQuadraticOptimizer(), 10);
     
            RecommendFactory.evaluate(RecommendFactory.EVALUATOR.AVERAGE_ABSOLUTE_DIFFERENCE, recommenderBuilder, null, model, 0.7);
            RecommendFactory.statsEvaluator(recommenderBuilder, null, model, 2);
     
            LongPrimitiveIterator iter = model.getUserIDs();
            while (iter.hasNext()) {
                long uid = iter.nextLong();
                //获得推荐结果，给uid推荐3个Item
                List list = recommenderBuilder.buildRecommender(model).recommend(uid, 3);
                RecommendFactory.showItems(uid, list, true);
            }
    	}catch(Exception e){
    		
    	}
        
    	

    }
}