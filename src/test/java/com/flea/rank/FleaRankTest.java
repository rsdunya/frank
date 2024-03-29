package com.flea.rank;

import com.flea.database.graph.FleaGraphDBManager;
import com.flea.database.graph.classes.FleaWebVertex;
import com.flea.database.indexer.FleaIndexer;
import com.flea.search.searcher.FleaSearcher;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by bilgi on 8/1/2015.
 */
public class FleaRankTest {

    FleaIndexer indexer;
    FleaSearcher searcher;
    //    FleaLanguageDetector languageDetector;
    FleaGraphDBManager graphDBManager;

    {
        ApplicationContext context =
                new ClassPathXmlApplicationContext("fleaCrawlerContext.xml");
//        languageDetector = (FleaLanguageDetector)context.getBean("fleaLanguageDetector");
        indexer = (FleaIndexer) context.getBean("fleaIndexer");
        searcher = (FleaSearcher) context.getBean("fleaSearcher");
        graphDBManager = (FleaGraphDBManager) context.getBean("fleaGraphDBManager");
    }

    // TODO in production we will need a way to cache ranked pages before updating the search indexer.
    // TODO updating the search indexer will be a trickle update, i.e slowly slowly over the course of hours
    // TODO the updated page ranks will appear in the search index.
    // TODO Distributed REDIS memory cache will be used to store these ranked pages.

    Map<String, Double> pageRankMap = new HashMap<String, Double>();

    @Test
    public void rankPages() {
        int startPage = 0;
        boolean more = true;
        long vertexCount = graphDBManager.countVertices(FleaWebVertex.NAME);
        double defaultRank = 1.0 / vertexCount;
        Iterator<FleaWebVertex> vertices = graphDBManager.getAllVertices("FleaWebVertex");
        DirectedSparseGraph directedSparseGraph = buildInMemGraph(vertices);
        PageRank pageRank = new PageRank(directedSparseGraph, 0.15);
//        pageRank.setMaxIterations(1000);
        pageRank.setTolerance(0.00001);
        pageRank.evaluate();

        double sum = 0;
        Iterator iterator = directedSparseGraph.getVertices().iterator();
        while (iterator.hasNext()) {
            Vertex vertex = (Vertex) iterator.next();
            double vertexScore = (double) pageRank.getVertexScore(vertex);
            vertex.setProperty("rank", vertexScore);
//            indexer.updateDoc(indexer.SITE_PAGES_INDEX, indexer.SITE_PAGE_TYPE, vertex.getProperty("indexDocId"), IndexerVars.RANK, vertexScore);
            sum += vertexScore;
        }
        System.out.println("sum: " + sum);
        /*double sum;
        int passCount = 1;
        do {
            sum = 0;
            ++passCount;
            vertices = graphDBManager.getVertices("FleaWebVertex", startPage);
            if (!vertices.hasNext()) {
                startPage = 0;
                more = false;
            }
            while (vertices.hasNext()) {
                OrientVertex next = vertices.next();
                double rank = 0;
                Iterable<Vertex> incomingLinks = next.getVertices(Direction.IN);
                Iterator<Vertex> iterator = incomingLinks.iterator();
                while (iterator.hasNext()) {
                    OrientVertex next1 = (OrientVertex) iterator.next();
                    Double rank1 = new Double(0);
                    if (next1.getProperty("rank") != null) {
                        rank1 = ((Number) next1.getProperty("rank")).doubleValue();
                    }
                    long l = next1.countEdges(Direction.OUT);
                    if (rank1 == null || rank1 == 0) {
                        rank1 = new Double(defaultRank);
                    }
                    if (l != 0)
                        rank1 -= (rank1 / l) * rank1;
                    pageRankMap.put(next.getProperty(IndexerVars.INDEX_DOC_ID), rank1);
                    graphDBManager.save(next1);
                    rank += (rank1);
                }
                sum += rank;
                if (rank == 0) {
                    rank = defaultRank;
                }
                next.setProperty("rank", rank);
                pageRankMap.put(next.getProperty(IndexerVars.INDEX_DOC_ID), rank);
                graphDBManager.save(next);
            }
            if (more) {
                ++startPage;
            }
            System.out.println("pass: " + passCount + " sum: " + sum + " default: " + defaultRank);
        } while (sum != 1.0);

        // We've updated the graph db, now lets update the search index
        Iterator<Map.Entry<String, Double>> iterator = pageRankMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Double> next = iterator.next();
            indexer.updateDoc(indexer.SITE_PAGES_INDEX, indexer.SITE_PAGE_TYPE, next.getKey(), IndexerVars.SCORE, next.getValue());
        }*/
    }

    private DirectedSparseGraph buildInMemGraph(Iterator<FleaWebVertex> vertices) {
        DirectedSparseGraph directedSparseGraph = new DirectedSparseGraph();
        while (vertices.hasNext()) {
            OrientVertex next = vertices.next();
            directedSparseGraph.addVertex(next);
            Iterable<Vertex> incomingLinks = next.getVertices(Direction.IN);
            Iterator<Vertex> iterator = incomingLinks.iterator();
            while (iterator.hasNext()) {
                OrientVertex next1 = (OrientVertex) iterator.next();
                directedSparseGraph.addVertex(next1);
                directedSparseGraph.addEdge(next.getEdges(next1, Direction.IN), next, next1);
            }
        }
        return directedSparseGraph;
    }
}