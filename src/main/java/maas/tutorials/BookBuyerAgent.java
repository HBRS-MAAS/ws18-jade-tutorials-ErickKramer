package maas.tutorials;

import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.basic.Action;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPANames;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.domain.JADEAgentManagement.ShutdownPlatform;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.List;
import java.util.Vector;
import java.util.Random;


@SuppressWarnings("serial")
public class BookBuyerAgent extends Agent {
	/*
		Book-Buyer agent periodically requests seller agents
		a book it was instructed to buy
	*/
	// List of strings containing the name of the books
	private List<String> booksCollection;
	// List of chosen books
	private List<String> chosenBooks;
	// List of bought books
	private List<String> booksBought;

	// Number of books to buy
	private int numBooksDesired = 3;


	// List of seller agents
	private AID [] sellerAgents;

	// Agent initialization
	protected void setup() {
	// Printout a welcome message
		System.out.println("----> Hello! Buyer-Agent "+ getAID().getName() +" is ready.");

		setupBooksCollection();
		chooseBooks();

		booksBought = new Vector<>();

		System.out.println("----> " + getAID().getName() + " Trying to buy " + chosenBooks);

		// Add Buyer to yellow Pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-buying");
		sd.setName("JADE-book-trading");
		dfd.addServices(sd);

		try{
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add a TickerBehaviour to schedule a request to seller agents every minute for each book
		for (String chosenBook : chosenBooks){
			addBehaviour(new TickerBehaviour(this, 4000){
				protected void onTick() {

					if (booksBought.contains(chosenBook)){
						System.out.println("----> " + getAID().getName() + " has already bought " + chosenBook);
						System.out.println("----> " + getAID().getName() + " bought ");
						System.out.println("----> " + booksBought);

						if (booksBought.size() == numBooksDesired){
							System.out.println("=============================");
							System.out.println("----> Agent " + getAID().getName() +" has finished shoping");
							System.out.println("=============================");
							doDelete();
							// addBehaviour(new shutdown());
						}
						stop();
					}else{
						// Update the list of seller SellerAgents
						DFAgentDescription template = new DFAgentDescription();
						ServiceDescription sd = new ServiceDescription();

						sd.setType("book-selling");
						template.addServices(sd);

						try {
							DFAgentDescription [] result = DFService.search(myAgent, template);
							System.out.println("----> Found " + result.length + " seller agents:");
							sellerAgents = new AID[result.length];
							for (int i = 0; i < result.length; ++i) {
								sellerAgents[i] = result[i].getName();
								System.out.println("----> " + sellerAgents[i].getName());
							}
						} catch (FIPAException fe) {
							fe.printStackTrace();

						}
						myAgent.addBehaviour(new RequestPerformer(chosenBook));
					}


				}
			});
			// System.out.println("----> Book: " + chosenBook);

		}



        try {
 			Thread.sleep(3000);
 		} catch (InterruptedException e) {
 			//e.printStackTrace();
 		}
		// addBehaviour(new shutdown());

	}
	// Shutting down the agent
	protected void takeDown() {
		 // Deregister from the yellow pages
 		try {
 			DFService.deregister(this);
 		}
 		catch (FIPAException fe) {
 			fe.printStackTrace();
 		}
		System.out.println("----> Buyer-Agent "+ getAID().getLocalName() + ": Terminating.");
	}

	// RequestPerformer class used to request books to the seller SellerAgents
	private class RequestPerformer extends Behaviour {
		private AID bestSeller;
		private int bestPrice;
		private int repliesCnt = 0;
		private MessageTemplate mt;
		private int step = 0;
		private String chosenBook;

		// Collect the given book
		public RequestPerformer(String chosenBook){
			this.chosenBook = chosenBook;
		}

		public void action(){
			switch (step){
				case 0:
					// Send CFP message to all senders
					ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

					for (int i = 0; i < sellerAgents.length; ++i){
						cfp.addReceiver(sellerAgents[i]);
					}

					cfp.setContent(chosenBook);
					cfp.setConversationId("book-trade");
					cfp.setReplyWith("cfp"+System.currentTimeMillis());
					myAgent.send(cfp);

					// Template for proposals
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
									MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
					step = 1;
					break;

				case 1:
					// Receive all proposals/refusals from seller agents
					ACLMessage reply = myAgent.receive(mt);

					if (reply != null){
						if (reply.getPerformative() == ACLMessage.PROPOSE){
							// Offer received
							int price = Integer.parseInt(reply.getContent());
							if (bestSeller == null || price < bestPrice){
								// This is the best offer at present
								bestPrice = price;
								bestSeller = reply.getSender();
							}
						}
						repliesCnt++;

						if (repliesCnt >= sellerAgents.length){
							// We received all replies
							step = 2;
						}
					}else{
						block();
					}
					break;

				case 2:
					// Send the purchase order to best seller
					ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
					order.addReceiver(bestSeller);
					order.setContent(chosenBook);
					order.setConversationId("book-trade");
					order.setReplyWith("order" + System.currentTimeMillis());
					myAgent.send(order);

					// Change template to purchase order reply
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
									MessageTemplate.MatchInReplyTo(order.getReplyWith()));
					step = 3;
					break;

				case 3:
					// Receive the purchase order reply
					reply = myAgent.receive(mt);
					if (reply != null){
						// Purchase order reply received
						if (reply.getPerformative() == ACLMessage.INFORM){
							// Purchase successful
							System.out.println("----> Agent " +getAID().getLocalName() +  " successfully purchased " + chosenBook + " from agent " + reply.getSender().getName());
							System.out.println("----> Price = " + bestPrice);
							booksBought.add(chosenBook);
						}else{
							System.out.println("----> Attempt failed: requested book already sold." );

						}

						step = 4;
					}else{
						block();
					}
					break;
			}
		}
		public boolean done() {
			if (step == 2 && bestSeller == null){
				System.out.println("Atempt failed: " + chosenBook + " not available for sale");
			}

			return ((step == 2 && bestSeller == null) || step == 4);
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

	public void setupBooksCollection(){
		booksCollection = new Vector<>();
		booksCollection.add("The beginning after the end - TurtleMe");
		booksCollection.add("Cirque du Freak - Darren Shan");
		booksCollection.add("The Analyst - John Katzenbach");
		booksCollection.add("Eragon - Christopher Paolini");
	}

	public void chooseBooks(){
		chosenBooks = new Vector<>();
		Random rand = new Random();
		while (chosenBooks.size() < numBooksDesired){
			int randomOption = rand.nextInt(booksCollection.size());
			boolean bookInChosenBoooks = chosenBooks.contains(booksCollection.get(randomOption));
			if (!bookInChosenBoooks){
				chosenBooks.add(booksCollection.get(randomOption));
			}

		}


	}
}
