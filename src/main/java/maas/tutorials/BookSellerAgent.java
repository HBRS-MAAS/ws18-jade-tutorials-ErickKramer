package maas.tutorials;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import java.util.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.basic.Action;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.domain.JADEAgentManagement.ShutdownPlatform;


public class BookSellerAgent extends Agent{

	// Catalogue of ebooks (maps title to its price)
	private Hashtable catalogueEbooks;
	// Number of ebook titles available
	private int numEbookTitles = 2;
	// Catalogue of paperback books (maps title to its price)
	private Hashtable cataloguePaperback;
	// Number of paperback titles available
	private int numPaperbackTitles = 2;
	// Inventory of paperback books (maps title to quantity of books)
	private Hashtable inventoryPaperback;
	// Total number of paperback books
	private int numPaperbackBooks = 20;

	// List of known buyers
	private AID [] buyerAgents;

	// Agent initialization
	protected void setup(){

		System.out.println("----> Hello! Seller-Agent "+getAID().getName()+" is ready.");

		setupCatalogues();

		// Register the book-selling service in Yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-selling");
		sd.setName("JADE-book-trading");
		dfd.addServices(sd);

		try{
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add the behaviour serving requests for offer from buyer agents
		addBehaviour(new OfferRequestsServer());

		// Add the behaviour serving purchase orders from buyers agents
		addBehaviour(new PurchaseOrdersServer());

		try {
 			Thread.sleep(3000);
 		} catch (InterruptedException e) {
 			//e.printStackTrace();
 		}

		// Add a TickerBehaviour to look for buyerAgents
		addBehaviour(new TickerBehaviour(this, 4000){
			protected void onTick(){
				// Update the list of buyer agents
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("book-buying");
				template.addServices(sd);
				try{
					DFAgentDescription[] result = DFService.search(myAgent, template);
					buyerAgents = new AID[result.length];
					for (int i = 0; i < result.length; ++i){
						buyerAgents[i] = result[i].getName();
					}
				} catch(FIPAException fe){
					fe.printStackTrace();
				}

				if (buyerAgents.length == 0){
					System.out.println("----> Seller-Agent " + getAID().getName() +" did not found any buyers");
					addBehaviour(new shutdown());
				}
			}
		});
	}

	// Shutting down the agent
	protected void takeDown() {
		// Deregister from the yellow pages
		try{
			DFService.deregister(this);
		} catch (FIPAException fe){
			fe.printStackTrace();
		}

		System.out.println("----> Seller-Agent "+getAID().getLocalName() + ": Terminating.");
	}

	public void setupCatalogues() {
		List<String> eBooks = new Vector<>();
		List<String> paperbackBooks = new Vector<>();
		List<Integer> eBooksPrice = new Vector<>();
		List<Integer> paperbackPrice = new Vector<>();
		Integer numBooks = (Integer) numPaperbackBooks / numPaperbackTitles;
		Random rand = new Random();

		eBooks.add("The beginning after the end - TurtleMe");
		eBooksPrice.add(rand.nextInt(50));
		eBooks.add("Cirque du Freak - Darren Shan");
		eBooksPrice.add(rand.nextInt(50));
		paperbackBooks.add("The Analyst - John Katzenbach");
		paperbackPrice.add(rand.nextInt(50));
		paperbackBooks.add("Eragon - Christopher Paolini");
		paperbackPrice.add(rand.nextInt(50));

		// Create the catalogues
		catalogueEbooks = new Hashtable();
		cataloguePaperback = new Hashtable();
		inventoryPaperback = new Hashtable();

		// Fill catalogues
		if (numEbookTitles == numPaperbackTitles){
			for(int i = 0; i < numEbookTitles; ++i){
				catalogueEbooks.put(eBooks.get(i), eBooksPrice.get(i));
				catalogueEbooks.put(paperbackBooks.get(i), paperbackPrice.get(i));
				inventoryPaperback.put(paperbackBooks.get(i), numBooks);
			}
		}

	}

	private class OfferRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null){
				// Message received
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				// Check if title in catalogues
				boolean titleInEbooks = catalogueEbooks.containsKey(title);
				boolean titleInPaper = cataloguePaperback.containsKey(title);

				if (titleInEbooks){
					// Get the price from the catalogue
					Integer price = (Integer) catalogueEbooks.get(title);

					// Send the price to the buyer
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(price.intValue()));
				}else if (titleInPaper){
					Integer price = (Integer) cataloguePaperback.get(title);
					Integer quantity = (Integer) inventoryPaperback.get(title);

					if (quantity > 0){
						// Send the price to the buyer
						reply.setPerformative(ACLMessage.PROPOSE);
						reply.setContent(String.valueOf(price.intValue()));
					}
				}else{
					// Book not available
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}else{
				block();
			}
		}
	}

	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action(){
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null){
				// ACCEPT_PROPOSAL received
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				// Check if title in catalogues
				boolean titleInEbooks = catalogueEbooks.containsKey(title);
				boolean titleInPaper = cataloguePaperback.containsKey(title);

				if (titleInEbooks){
					// Send the price to the buyer
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println("----> " + title + " sold to agent " +msg.getSender().getName());
				}else if (titleInPaper){
					Integer quantity = (Integer) inventoryPaperback.get(title);

					if (quantity > 0){
						// Send the price to the buyer
						reply.setPerformative(ACLMessage.INFORM);
						System.out.println("----> " + title + " sold to agent " +msg.getSender().getName());
						// Update inventory
						inventoryPaperback.put(title, quantity--);
					}
				}else{
					// Book not available
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}else{
				block();
			}
		}
	}

	// Taken from http://www.rickyvanrijn.nl/2017/08/29/how-to-shutdown-jade-agent-platform-programmatically/
	private class shutdown extends OneShotBehaviour{
		public void action() {
			ACLMessage shutdownMessage = new ACLMessage(ACLMessage.REQUEST);
			Codec codec = new SLCodec();
			myAgent.getContentManager().registerLanguage(codec);
			myAgent.getContentManager().registerOntology(JADEManagementOntology.getInstance());
			shutdownMessage.addReceiver(myAgent.getAMS());
			shutdownMessage.setLanguage(FIPANames.ContentLanguage.FIPA_SL);
			shutdownMessage.setOntology(JADEManagementOntology.getInstance().getName());
			try {
			    myAgent.getContentManager().fillContent(shutdownMessage,new Action(myAgent.getAID(), new ShutdownPlatform()));
			    myAgent.send(shutdownMessage);
			}
			catch (Exception e) {
			    //LOGGER.error(e);
			}

		}
	}
}
