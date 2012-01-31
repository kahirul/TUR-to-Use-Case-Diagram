package org.itb.informatika.nlp;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Extract actor - use cases from text.
 * 
 * @author Hajr
 */
public class UseCaseDiagramElementExtractor {

	private Map<String, List<String>> association;	
	private Map<String, CorefMention> corefReplacedLookup;
	private List<SemanticGraphEdge> discoverdObjEdges;
	private Set<String> rawActors;
	private Set<String> nounTags;
	private Set<String> verbTags;
	private Set<String> toBe;
	private String determiner;
	private Properties props;	

	public UseCaseDiagramElementExtractor() {
		this.determiner = "another,other,a,an,the,many,much,few,little,couple,several,most,this,that,these,those,each,every,any,some,all";

		this.verbTags = new HashSet<String>(Arrays.asList("VBN", "VB", "VBP", "VBG", "VBD", "VBZ"));
		this.toBe = new HashSet<String>(Arrays.asList("is", "am", "are", "were", "was"));
		this.nounTags = new HashSet<String>(Arrays.asList("NN", "NNS", "NNP", "NNPS"));
		this.discoverdObjEdges = new ArrayList<SemanticGraphEdge>();
		this.rawActors = new HashSet<String>();

		this.props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, parse, lemma, ner, dcoref");

	}
	
	private void extractCorefChain(Map<Integer, CorefChain> corefCain) {
		CorefMention representativeMention;
		List<CorefMention> corefMentions;
		
		for (Map.Entry<Integer, CorefChain> entry : corefCain.entrySet()) {
			corefMentions = entry.getValue().getCorefMentions();
			if (corefMentions.size() > 1) {
				representativeMention = entry.getValue().getRepresentativeMention();

				for (CorefMention cm : corefMentions) {
					String lookupKey = cm.sentNum + " - " + cm.mentionSpan;
					corefReplacedLookup.put(lookupKey, representativeMention);
				}
			}
		}
	}

	private String getSingularForm(String input) {
		String sf = input;
		int il = input.length();
		
		if(rawActors.contains(input.substring(0, il - 1)) && input.substring(il - 1, il).equals("s")){
			sf = input.substring(0, il - 1);
		}
		
		if(rawActors.contains(input.substring(0, il - 2)) && input.substring(il - 2, il).equals("es")){
			sf = input.substring(0, il - 2);
		}
		
		return sf;
	}
	
	private String removeDeterminer(String input) {
		Set<String> search = new HashSet<String>(Arrays.asList(determiner.split(",")));
		String[] inputs = input.split("\\s+");
		StringBuilder output = new StringBuilder();

		for (String word : inputs) {
			if (!search.contains(word)) {
				output.append(word);
				output.append(" ");
			}
		}
		return output.toString().trim();
	}

	private String getNounModifier(List<SemanticGraphEdge> edges, SemanticGraphEdge edge) {
		String nn = null;
		for (SemanticGraphEdge searchEdge : edges) {
			// Search for compound noun modifier
			if (searchEdge.toString().equalsIgnoreCase("nn") && searchEdge.getSource().equals(edge.getTarget())) {
				nn = searchEdge.getTarget().word();
			}
		}
		return nn;
	}

	private String getPrepOf(List<SemanticGraphEdge> edges, SemanticGraphEdge edge) {
		String prep_of = null;
		for (SemanticGraphEdge searchEdge : edges) {
			// Search for preposition of
			if (searchEdge.toString().equalsIgnoreCase("prep_of") && searchEdge.getSource().equals(edge.getTarget())) {
				prep_of = searchEdge.getTarget().word();
			}
		}
		return prep_of;
	}

	private String getAdjMod(List<SemanticGraphEdge> edges, SemanticGraphEdge edge) {
		String amod = null;
		for (SemanticGraphEdge searchEdge : edges) {
			// Search for preposition of
			if (searchEdge.toString().equalsIgnoreCase("amod") && searchEdge.getSource().equals(edge.getTarget())) {
				amod = searchEdge.getTarget().word();
			}
		}

		return amod;
	}

	private boolean isHavingRCModifier(List<SemanticGraphEdge> edges, SemanticGraphEdge edge) {
		boolean rcmod = false;
		// Handle relative clause modifer, e.g. "I saw the book which you bought"
		for (SemanticGraphEdge searchEdge : edges) {
			if (searchEdge.toString().equalsIgnoreCase("rcmod")) {
				rcmod = searchEdge.getTarget().equals(edge.getSource());
			}
		}
		return rcmod;
	}

	private String getPosessivedNoun(List<SemanticGraphEdge> edges, SemanticGraphEdge edge, int sentNum) {
		String ps = null;

		for (SemanticGraphEdge possEdge : edges) {
			if (possEdge.toString().equalsIgnoreCase("poss")) {
				if (possEdge.getSource().equals(edge.getTarget())) {
					ps = possEdge.getTarget().word();
					ps = getCorefResolvedNoun(ps, sentNum + " - " + ps);
				}
			}
		}
		return ps;
	}

	public String getCompleteNounPhrase(String base, List<SemanticGraphEdge> edges, SemanticGraphEdge edge, int sentNum) {
		String np = base;
		String lookupKey = sentNum + " - " + base;
		
		np = getCorefResolvedNoun(np, lookupKey );

		String ps = getPosessivedNoun(edges, edge, sentNum);
		if (ps != null) {
			np = ps + "'s " + np;
		}

		String nn = getNounModifier(edges, edge);
		if (nn != null) {
			np = nn + " " + np;
		}
		String amod = getAdjMod(edges, edge);
		if (amod != null) {
			np = amod + " " + np;
		}
		
		String prep_of = getPrepOf(edges, edge);
		if (prep_of != null) {
			np = np + " of " + prep_of;
		}

		return np;
	}

	private String getCorefResolvedNoun(String input, String lookupKey) {
		String resolved = input;
		if (corefReplacedLookup.containsKey(lookupKey)) {
			CorefMention referent = corefReplacedLookup.get(lookupKey);			
			resolved = referent.mentionSpan;
		}
		return resolved;
	}

	private IndexedWord getPredicateIdxWord(List<SemanticGraphEdge> edges, SemanticGraphEdge subjEdge) {
		IndexedWord piw = subjEdge.getSource();
		for (SemanticGraphEdge edge : edges) {
			if (edge.toString().equalsIgnoreCase("xcomp") && subjEdge.getSource().equals(edge.getSource())) {
				piw = edge.getTarget();
			}
		}
		return piw;
	}

	/**
	 * Return an unmodifiable map containing association between actor and actions	 * 
	 * @return an unmodifiable map containing association between actor and actions
	 */
	public Map<String, List<String>> getAssociation() {
		return Collections.unmodifiableMap(association);
	}

	/**
	 * Create association between actor and actions from triplet SPO (Subject - Predicate - Object)
	 * @param rawTriplets triplet SPO
	 */
	public void createAssociation(List<String[]> rawTriplets) {
		List<String> tmp = null;
		String subjectPart = "";
		for (String[] triple : rawTriplets) {
			subjectPart = getSingularForm(triple[0].toLowerCase());
			subjectPart = removeDeterminer(subjectPart);

			// Kalo udah ada subject di dalam, masukan subject baru
			if (association.containsKey(subjectPart.toLowerCase())) {
				tmp = association.get(subjectPart.toLowerCase());

				// Kalo belum ada aksi, masukan aksi baru
				if (!tmp.contains(triple[1] + " " + triple[2])) {
					association.get(subjectPart.toLowerCase()).add(triple[1] + " " + triple[2]);
				}
			} else {
				tmp = new ArrayList<String>();
				tmp.add(triple[1] + " " + triple[2]);
				association.put(subjectPart.toLowerCase(), tmp);
			}
		}
	}

	/**
	 * Parse text TUR and extract actor - action
	 * @param TUR 
	 */
	public void extractUseCaseDiagramElement(String TUR) {
		this.association = new HashMap<String, List<String>>();
		this.corefReplacedLookup = new HashMap<String, CorefMention>();

		Annotation document = new Annotation(TUR);
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		pipeline.annotate(document);

		// Create coref chain association;
		extractCorefChain(document.get(CorefChainAnnotation.class));
		
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		List<String[]> rawTriplets = new ArrayList<String[]>();
		IndexedWord predicateIdxWord;
		SemanticGraph dependencies;
		
		String[] tmpTriplesItem = new String[3];
		String subject = "";
		String predicate = "";
		String object = "";
		int sentNum = 0;

		// Extract SPO from sentences
		for (CoreMap sentence : sentences) {
			
			dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
			discoverdObjEdges.clear();
			tmpTriplesItem = null;
			sentNum++;

			for (SemanticGraphEdge subjEdge : dependencies.edgeListSorted()) {
				// Get nsubj with target in noun group
				if ((subjEdge.toString().equalsIgnoreCase("nsubj") || subjEdge.toString().equalsIgnoreCase("nsubjpass")) && (nounTags.contains(subjEdge.getTarget().tag())|| subjEdge.getTarget().tag().equalsIgnoreCase("PRP"))) {

					// Check apakah head adalah sebuah aksi (verb) dan mengabaikan clausa attributive, e.g. 'Tom is president of USA' dan ignore subj yang berada pada sub clause,					
					if (verbTags.contains(subjEdge.getSource().tag()) && !toBe.contains(subjEdge.getSource().word().toLowerCase()) && !isHavingRCModifier(dependencies.edgeListSorted(), subjEdge)) {

						// Handle non finite clause complement e.g. He likes to swim						
						predicateIdxWord = getPredicateIdxWord(dependencies.edgeListSorted(), subjEdge);

						if (predicateIdxWord.equals(subjEdge.getSource()) && subjEdge.toString().equalsIgnoreCase("nsubjpass")) {
							// Init temp variable						
							subject = "SYSTEM";
							predicate = predicateIdxWord.lemma();
							object = subjEdge.getTarget().word();
							object = getCompleteNounPhrase(object, dependencies.edgeListSorted(), subjEdge, sentNum);

							for (SemanticGraphEdge objEdge : dependencies.edgeListSorted()) {
								// Kalo head clausa punya direct object
								if (objEdge.toString().equalsIgnoreCase("agent") && objEdge.getSource().equals(subjEdge.getSource())) {
									// Direct objek jadi subjek clausa aktif
									subject = objEdge.getTarget().lemma();
									subject = getCompleteNounPhrase(subject, dependencies.edgeListSorted(), objEdge, sentNum);
								}
							}
						} else {
							// Init temp variable						
							subject = subjEdge.getTarget().lemma();
							subject = getCompleteNounPhrase(subjEdge.getTarget().word(), dependencies.edgeListSorted(), subjEdge, sentNum);
							predicate = predicateIdxWord.lemma();
							object = "";

							// Looking for clause object							
							for (SemanticGraphEdge objEdge : dependencies.edgeListSorted()) {
								if (objEdge.toString().equalsIgnoreCase("dobj") && objEdge.getSource().equals(predicateIdxWord)) {
									object = objEdge.getTarget().word();
									object = getCompleteNounPhrase(object, dependencies.edgeListSorted(), objEdge, sentNum);

									discoverdObjEdges.add(objEdge);
								}
							}
						}
					
						tmpTriplesItem = new String[3];
						tmpTriplesItem[0] = subject;
						tmpTriplesItem[1] = predicate;
						tmpTriplesItem[2] = object;
						rawTriplets.add(tmpTriplesItem);
						rawActors.add(subject.toLowerCase());
					}
				} else if (subjEdge.toString().equalsIgnoreCase("dobj") && !isHavingRCModifier(dependencies.edgeListSorted(), subjEdge)) {

					// Object with implicit subject
					if (!discoverdObjEdges.contains(subjEdge) && (tmpTriplesItem != null) && !tmpTriplesItem[0].equals("SYSTEM")) {
						subject = tmpTriplesItem[0];
						predicate = subjEdge.getSource().lemma();
						object = subjEdge.getTarget().word();

						object = getCompleteNounPhrase(object, dependencies.edgeListSorted(), subjEdge, sentNum);

						tmpTriplesItem = new String[3];
						tmpTriplesItem[0] = subject;
						tmpTriplesItem[1] = predicate;
						tmpTriplesItem[2] = object;
						rawTriplets.add(tmpTriplesItem);
						rawActors.add(subject.toLowerCase());
					}
				}
			}
		}

		createAssociation(rawTriplets);
		int a = 0;
	}
}
