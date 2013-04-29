// Speed Scrabble v3.5

import java.util.*;
import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.Timer;

abstract class Client
{
	static final int WAIT_TIME = 60000; // in milliseconds
	
	PrintWriter writer;
	BufferedReader reader;
	
	Game game;
	final String name;
	int playerIndex;
	boolean open;
	
	Client(String name)
	{
		this.name = name;
		game = new Game();
	}
	
	void connect(byte[] address, int port) throws IOException
	{
		Socket socket = new Socket();
		
		socket.connect(new InetSocketAddress(InetAddress.getByAddress(address), port), WAIT_TIME);
		
		writer = new PrintWriter(socket.getOutputStream(), true);
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		
		writer.println("NEW_CLIENT " + name.replace(' ', '\0'));
		
		String[] codes = getCodes();
		// WELCOME [player index]
		if (!codes[0].equals("WELCOME"))
		{
			socket.close();
			throw new IOException();
		}		
		playerIndex = Integer.parseInt(codes[1]);
		
		startListener();
		
		open = true;
	}
	
	void close() throws IOException
	{
		if (writer != null)
			writer.close();
		if (reader != null)
			reader.close();
		
		open = false;
	}
	
	void flipTile()
	{
		writer.println("FLIP");
	}
	
	void attemptTake(String word)
	{
		writer.println("TAKE " + word);
	}
	
	void requestNewGame()
	{
		writer.println("NEW");
	}
	
	void requestRejection(int index)
	{
		writer.println("REJECT " + index);
	}
	
	void sendMessage(String s)
	{
		writer.println("MESSAGE " + playerIndex + " " + s.replace(' ', '\0'));
	}
	
	abstract void startListener();
	
	protected String[] getCodes() throws IOException
	{
		String line = reader.readLine();
		return (line == null ? null : line.split(" "));
	}
	
	protected void newGame(String[] codes)
	{
		int numPlayers = Integer.parseInt(codes[1]);
		String[] playerNames = new String[numPlayers];
		for (int i = 0; i < numPlayers; i++)
			playerNames[i] = codes[i + 2].replace('\0', ' ');
		int[] newTiles = new int[Game.NUM_TILES];
		for (int i = 0; i < Game.NUM_TILES; i++)
			newTiles[i] = Integer.parseInt(codes[i + numPlayers + 2]);
		game.newGame(numPlayers, playerNames, newTiles);
	}
}

class HumanClient extends Client
{
	final GameFrame frame;
	
	HumanClient (String name)
	{
		super(name);
		frame = new GameFrame(this);
	}
	
	void connect(byte[] address, int port) throws IOException
	{
		super.connect(address, port);
		frame.setVisible(true);
	}
	
	void close() throws IOException
	{
		frame.destroy();
		super.close();
	}
	
	void startListener()
	{
		new Thread()
		{
			public void run()
			{
				while (true)
				{
					try
					{
						String[] codes = getCodes();
						
						if (codes == null)
							throw new IOException();
						
						if (codes[0].equals("NEW"))
						{
							// NEW [numPlayers] [player name] [player name] ... [letter] [letter] [letter] ...
							newGame(codes);
							if (!frame.started) frame.start();
							frame.showMessage("NEW GAME STARTED");
							frame.changeAll();
						}
						else if (codes[0].equals("FLIP"))
						{
							// FLIP [letter index]
							game.flipTile(Integer.parseInt(codes[1]));
							frame.changePile();
						}
						else if (codes[0].equals("TAKE"))
						{
							// TAKE [change data]
							ChangeData data = ChangeData.parse(codes[1].replace('\0', ' '));
							game.processChange(data);
							frame.changeWordPanels(data.playerTaken, data.playerStolen);
						}
						else if (codes[0].equals("REJECT_REQ"))
						{
							// REJECT_REQ [ChangeData index]
							int index = Integer.parseInt(codes[1]);
							ChangeData data = game.lastSteals.get(index);
							
							frame.openRejectConfirmBox(data.stolen, data.taken, index);
						}
						else if (codes[0].equals("UNDO"))
						{
							// UNDO [ChangeData index] [word]
							game.undoChanges(Integer.parseInt(codes[1]));
							frame.showMessage("\"" + codes[2] + "\" REJECTED");
							frame.changeAll();
						}
						else if (codes[0].equals("MESSAGE"))
						{
							// MESSAGE [player index] [message]
							frame.newMessage(game.playerNames[Integer.parseInt(codes[1])], codes[2].replace('\0', ' '));
						}
						else if (codes[0].equals("SHOW"))
						{
							// SHOW [message]
							frame.showMessage(codes[1].replace('\0', ' '));
						}
					}
					catch (IOException e)
					{
						JOptionPane.showMessageDialog(frame, "Disconnected to server.");
						
						try
						{
							close();
						}
						catch (IOException e_)
						{
							JOptionPane.showMessageDialog(frame, "Error.");
						}
						
						return;
					}
				}
			}
		}.start();
	}
}

class ComputerClient extends Client
{
	static final int SPEED = 3;
	static final int FLIP_TIME = 0;
	
	boolean started;

	ComputerClient(String name)
	{
		super(name);
		game = new ComputerGame();
	}
	
	void startListener()
	{
		new Thread()
		{
			public void run()
			{
				while (true)
				{
					try
					{
						String[] codes = getCodes();
						
						if (codes == null)
							throw new IOException();
						
						if (codes[0].equals("NEW"))
						{
							// NEW [numPlayers] [player name] [player name] ... [letter] [letter] [letter] ...
							newGame(codes);
							if (!started) started = true;
						}
						else if (codes[0].equals("FLIP"))
						{
							// FLIP [letter index]
							game.flipTile(Integer.parseInt(codes[1]));
							
						}
						else if (codes[0].equals("TAKE"))
						{
							// TAKE [change data]
							ChangeData data = ChangeData.parse(codes[1].replace('\0', ' '));
							game.processChange(data);
							
						}
						else if (codes[0].equals("REJECT_REQ"))
						{
							// REJECT_REQ [ChangeData index]
							int index = Integer.parseInt(codes[1]);						
							requestRejection(index);
						}
						else if (codes[0].equals("UNDO"))
						{
							// UNDO [ChangeData index] [word]
							game.undoChanges(Integer.parseInt(codes[1]));
							
						}
						else if (codes[0].equals("SHOW"))
						{
							// SHOW [message]
							if (codes[1].equals("New game requested!".replace(' ', '\0')))
								requestNewGame();
						}
					}
					catch (IOException e)
					{
						try
						{
							started = false;
							close();
						}
						catch (IOException e_) {}
						
						return;
					}
				}
			}
		}.start();
		
		new Thread()
		{
			public void run()
			{
				try
				{
					while (!started)
						try { Thread.sleep(100); } catch (InterruptedException e) {}
					while (started)
					{
						int index = (int)(Math.random() * Dictionary.size());
						if (((ComputerGame)game).canTake(Dictionary.getWord(index)))
						{
							//System.out.println("WORD FOUND: " + Dictionary.getWord(index));
							//System.out.println(Arrays.toString(((ComputerGame)game).charCounts));
							attemptTake(Dictionary.getWord(index));
						}
						
						if (playerIndex == game.currentPlayer && game.remainingTime() <= Math.max(0, Game.MAX_FLIP_TIME - FLIP_TIME))
						{
							flipTile();
						}
						
						index++;
						if (index % SPEED == 0)
							try { Thread.sleep(1); } catch (InterruptedException e) {}
						if (index >= Dictionary.size())
						{
							index = 0;
							//System.out.println(Arrays.toString(((ComputerGame)game).charCounts));
						}
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
					return;
				}
			}
		}.start();
	}
}

class ComputerGame extends Game
{
	int[] charCounts;
	LinkedList<Integer>[] currentLetters;
	LinkedList<Word> words;
	
	ComputerGame()
	{
		currentLetters = (LinkedList<Integer>[]) new LinkedList[NUM_LETTERS];
		for (int i = 0; i < NUM_LETTERS; i++)
			currentLetters[i] = new LinkedList<Integer>();
			
		words = new LinkedList<Word>();
	}
	
	void newGame(int numPlayers, String[] playerNames, int[] newTiles)
	{
		charCounts = new int[NUM_LETTERS];
		for (int i = 0; i < NUM_LETTERS; i++)
			currentLetters[i].clear();
		
		words.clear();
		words.add(EMPTY);
		
		super.newGame(numPlayers, playerNames, newTiles);
	}
	
	void flipTile(int index)
	{
		int letter = tiles[index].letter;
		
		super.flipTile(index);
		charCounts[letter]++;
		currentLetters[letter].add(index);
	}
	
	void processChange(ChangeData data)
	{
		int[] charWatch = new int[NUM_LETTERS];
		
		if (data.stolen != EMPTY)
			words.remove(data.stolen);
		
		Word taken = new Word(data.taken.word, data.playerTaken);
		words.add(taken);
		
		updateCharCount(data.taken.word, data.stolen == null ? "" : data.stolen.word, charWatch, data);
		
		super.processChange(data);
	}
	
	void undo(ChangeData lastSteal)
	{
		words.remove(lastSteal.taken);
		if (lastSteal.stolen != null)
			words.add(lastSteal.stolen);
		
		super.undo(lastSteal);
	}
	
	boolean canTake(String newWord)
	{
		if (newWord.length() < 3)
			return false;
		
		for (int i = 0; i < words.size(); i++)
			if (words != null && isStealable(newWord, words.get(i).word)) // FIX THIS LATER
				return true;
				
		return false;
	}
	
	private boolean isStealable(String newWord, String word)
	{
		if (word.length() >= newWord.length())
			return false;
			
		int[] table = new int[NUM_LETTERS];
		
		for (char c : newWord.toCharArray())
			table[c - 'A']++;
		for (char c : word.toCharArray())
		{
			table[c - 'A']--;
			if (table[c - 'A'] < 0)
				return false;
		}
		for (int i = 0; i < NUM_LETTERS; i++)
			if (table[i] > charCounts[i])
				return false;
				
		return true;
	}

	private void updateCharCount(String newWord, String toSteal, int[] charWatch, ChangeData data)
	{
		int[] table = new int[NUM_LETTERS];
		for (char c : newWord.toCharArray())
			table[c - 'A']++;
		for (char c : toSteal.toCharArray())
			table[c - 'A']--;
		for (int i = 0; i < NUM_LETTERS; i++)
		{
			charCounts[i] -= table[i];
			for (int j = 0; j < table[i]; j++)
			{
				int index = currentLetters[i].removeFirst();
				int movedIndex = data.taken.indexOf(i + 'A', charWatch[i]);
				charWatch[i] = movedIndex + 1;
				data.tileMoves.add(new int[] {index, -1, 0, 0, data.playerTaken,
					(data.playerStolen == data.playerTaken ? data.stolenIndex : wordPiles[data.playerTaken].size()), movedIndex});
			}
		}
	}
}

class Game
{
	static final int NUM_LETTERS = 26;
	static final int NUM_TILES = 98;
	static final int[] FREQUENCIES = {9, 2, 2, 4, 12, 2, 3, 2, 9, 1, 1, 4, 2, 6, 8, 2, 1, 6, 4, 6, 4, 2, 2, 1, 2, 1};
	static final Word EMPTY = new Word("", -1);
	static final ChangeData NO_WORD = new ChangeData();
	static final int MIN_LENGTH = 3;
	static final int MAX_LENGTH = 15;
	static final int MAX_FLIP_TIME = 20;
	static final int MAX_PLAYERS = 3;
	
	int numPlayers;
	boolean started;
	
	Tile[] tiles;
	int lastFlipIndex;
	long lastFlipTime;
	LinkedList<Word>[] wordPiles;
	Stack<ChangeData> lastSteals;
	
	String[] playerNames;
	boolean[] presentPlayers;
	int[] scores;
	int currentPlayer;
	
	Game()
	{
		numPlayers = 0;
		started = false;
	}
	
	void newGame(int numPlayers, String[] playerNames, int[] newTiles)
	{
		this.numPlayers = numPlayers;
		
		tiles = new Tile[NUM_TILES];
		for (int i = 0; i < NUM_TILES; i++)
			tiles[i] = new Tile(newTiles[i], i);
		lastFlipIndex = -1;
		lastFlipTime = -1;
		wordPiles = (LinkedList<Word>[])new LinkedList[numPlayers];
		for (int i = 0; i < numPlayers; i++)
			wordPiles[i] = new LinkedList<Word>();
		lastSteals = new Stack<ChangeData>();
			
		this.playerNames = playerNames;
		presentPlayers = new boolean[numPlayers];
		for (int i = 0; i < numPlayers; i++)
			presentPlayers[i] = true;
		scores = new int[numPlayers];
		for (int i = 0; i < numPlayers; i++)
			scores[i] = 0;			
		currentPlayer = 0;
		
		started = true;
	}
	
	void flipTile(int index)
	{
		if (!started)
			return;
		
		tiles[index].flip();
		lastFlipIndex = index;
		lastFlipTime = System.currentTimeMillis();
		nextPlayer();
	}
	
	void processChange(ChangeData data)
	{
		if (data.stolen != null)
		{
			wordPiles[data.playerStolen].remove(data.stolenIndex);
			scores[data.playerStolen] -= data.stolen.length();
		}
		
		if (data.playerStolen == data.playerTaken)
			wordPiles[data.playerTaken].add(data.stolenIndex, data.taken);
		else
			wordPiles[data.playerTaken].add(data.taken);
		currentPlayer = data.playerTaken;
		scores[data.playerTaken] += data.taken.length();
		
		for (int[] move : data.tileMoves)
			tiles[move[0]].move(move[4], move[5], move[6]);
		
		lastSteals.push(data);
	}
	
	void undoChanges(int index)
	{
		while (lastSteals.size() > index)
			undo(lastSteals.pop());
	}
	
	void undo(ChangeData lastSteal)
	{
		if (lastSteal.stolen != null)
		{
			wordPiles[lastSteal.playerStolen].add(lastSteal.stolenIndex, lastSteal.stolen);
			scores[lastSteal.playerStolen] += lastSteal.stolen.length();
		}
		
		wordPiles[lastSteal.playerTaken].removeLast();
		if (lastSteal.playerStolen != -1)
			currentPlayer = lastSteal.playerStolen;
		scores[lastSteal.playerTaken] -= lastSteal.taken.length();
		
		for (int[] move : lastSteal.tileMoves)
			tiles[move[0]].move(move[1], move[2], move[3]);
	}
	
	long remainingTime()
	{
		return Math.max(0, MAX_FLIP_TIME - (System.currentTimeMillis() - lastFlipTime) / 1000);
	}
	
	boolean allGone()
	{
		for (boolean present : presentPlayers)
			if (present)
				return false;
				
		return true;
	}
	
	private void nextPlayer()
	{
		int lastPlayer = currentPlayer;
		
		do
		{
			currentPlayer++;
			
			if (currentPlayer >= numPlayers)
				currentPlayer = 0;
		}
		while (currentPlayer != lastPlayer && !presentPlayers[currentPlayer]);
	}
}

class GameFrame extends JFrame
{
	Client client;
	
	JDesktopPane pane;
	JButton newGame, reject, chat, leave;
	InfoPanel infoPanel;
	GamePanel gamePanel;
	Chatbox chatbox;
	
	boolean started;
	Timer timer1, timer2;
	
	GameFrame(Client client)
	{
		this.client = client;
		pane = new JDesktopPane();
		newGame = new JButton("NEW GAME");
		reject = new JButton("REJECT");
		chat = new JButton("CHAT");
		leave = new JButton("LEAVE");		
		infoPanel = new InfoPanel(client);
		gamePanel = new GamePanel(client);
		chatbox = new Chatbox(client);
		setupFrame();
		changeAll();
		started = false;
	}
	
	void destroy()
	{
		timer1.stop();
		timer2.stop();
		dispose();
		started = false;
	}
	
	void start()
	{
		if (started)
			return;
			
		started = true;
		
		infoPanel.playerIndex = client.playerIndex;
		gamePanel.playerIndex = client.playerIndex;
		gamePanel.setNumPlayers(client.game.numPlayers);
		
		addButtonListeners();
		addFrameListeners();
				
		timer1 = new Timer(50, new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				repaint();
			}
		});
		
		timer2 = new Timer(1000, new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				changeAll();
			}
		});
		
		setFocusable(true);
		requestFocus(true);
		
		timer1.start();
		timer2.start();
	}
	
	void showMessage(String s)
	{
		infoPanel.newMessage = s;
		infoPanel.lastMessageTime = System.currentTimeMillis();
	}
	
	void newMessage(String name, String s)
	{
		chatbox.appendLine(name, s);
		if (!chatbox.isVisible())
			chat.setText("NEW CHAT");
	}
	
	void openChatbox()
	{
		if (chatbox.isVisible())
			return;
		
		pane.add(chatbox);
		chatbox.setVisible(true);
		chatbox.moveToFront();
	}
	
	void openRejectbox()
	{
		Rejectbox rejectbox = new Rejectbox(client);
		pane.add(rejectbox);
		rejectbox.setVisible(true);
	}
	
	void openRejectConfirmBox(Word stolen, Word taken, int index)
	{
		RejectConfirmBox confirmBox = new RejectConfirmBox(client, stolen, taken, index);
		pane.add(confirmBox);
		confirmBox.setVisible(true);
	}
	
	void changeAll()
	{
		gamePanel.pileChange = true;
		
		for (int i = 0; i < client.game.numPlayers; i++)
			gamePanel.wordPanelsChange[i] = true;
	}
	
	void changeWordPanels(int ... playerIndeces)
	{
		gamePanel.pileChange = true;
		
		for (int i : playerIndeces)
			if (i != -1)
				gamePanel.wordPanelsChange[(gamePanel.playerIndex + i) % client.game.numPlayers] = true;
	}
	
	void changePile()
	{
		gamePanel.pileChange = true;
	}
	
	private void setupFrame()
	{
		setFrameProperties();		
		addAll();
	}
	
	private void addButtonListeners()
	{
		newGame.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				client.requestNewGame();
			}
		});
		
		reject.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				openRejectbox();
			}
		});
		
		chat.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				openChatbox();
				chat.setText("CHAT");
			}
		});
		
		leave.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				try
				{
					client.close();
				}
				catch (IOException e_)
				{
					JOptionPane.showMessageDialog(null, "Error.");
				}
			}
		});
	}
	
	private void addFrameListeners()
	{
		addKeyListener(new KeyAdapter()
		{
			public void keyPressed(KeyEvent e)
			{
				gamePanel.process(e.getKeyCode());
				repaint();
			}
		});
		
		addMouseListener(new MouseAdapter()
		{
			public void mousePressed(MouseEvent e)
			{
				requestFocus(true);
			}
		});
	}
	
	private void setFrameProperties()
	{
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		
		setBackground(Color.BLACK);
		setResizable(false);
		setSize(d.width, d.height - 30);
		setUndecorated(true);
		
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing()
			{
				try
				{
					client.close();
				}
				catch (IOException e_)
				{
					JOptionPane.showMessageDialog(null, "Error.");
				}
			}
		});
	}
	
	private void addAll()
	{
		int width = Toolkit.getDefaultToolkit().getScreenSize().width;
		
		pane.add(newGame);
		newGame.setLocation(10, 10);
		newGame.setSize(100, 30);
		
		pane.add(reject);
		reject.setLocation(115, 10);
		reject.setSize(100, 30);
		
		pane.add(chat);
		chat.setLocation(width - 215, 10);
		chat.setSize(100, 30);
		
		pane.add(leave);
		leave.setLocation(width - 110, 10);
		leave.setSize(100, 30);
		
		pane.add(infoPanel);
		infoPanel.setLocation(220, 10);
		infoPanel.setSize(width - 440, 30);
		
		pane.add(gamePanel);
		gamePanel.setLocation(width / 2 - 620, 45);
		gamePanel.setSize(1240, 720);
		
		add(pane);
	}
}

class GamePanel extends JPanel
{
	static final double MOVE_SPEED = .2;
	static final Rectangle PILE = new Rectangle(250, 5, 740, 390);
	static final Rectangle INPUT_PANEL = new Rectangle(320, 655, 600, 60);
	static final Rectangle[] WORD_PANELS = 
		{
			new Rectangle(270, 400, 700, 250),
			new Rectangle(5, 5, 240, 700),
			new Rectangle(995, 5, 240, 700)
		};
	/*static final Rectangle PILE = new Rectangle(450, 5, 740, 390);
	static final Rectangle INPUT_PANEL = new Rectangle(520, 605, 600, 60);
	static final Rectangle[] WORD_PANELS = 
		{
			new Rectangle(470, 400, 700, 200),
			new Rectangle(5, 5, 440, 700)
		};*/
	
	Client client;
	Game game;
	TileSpot[] spots;
	StringBuilder word;
	int playerIndex;
	
	BufferedImage pile;
	BufferedImage inputPanel;
	WordPanel[] wordPanels;
	boolean pileChange;
	boolean inputPanelChange;
	boolean[] wordPanelsChange;
	
	GamePanel(Client client)
	{
		this.client = client;
		game = client.game;
		setupSpots();
		word = new StringBuilder();
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		try
		{
			if (!game.started)
				return;
			
			if (pileChange)
				drawPile();
			g.drawImage(pile, PILE.x, PILE.y, null);
			pileChange = false;
			
			if (inputPanelChange)
				drawInputPanel();
			g.drawImage(inputPanel, INPUT_PANEL.x, INPUT_PANEL.y, null);
			inputPanelChange = false;
			
			if (wordPanelsChange != null)
				for (int i = 0; i < game.numPlayers; i++)
				{
					if (wordPanelsChange[i])
						wordPanels[i].draw();
						
					g.drawImage(wordPanels[i].image, WORD_PANELS[i].x, WORD_PANELS[i].y, null);
					wordPanelsChange[i] = false;
				}
			
			// draw all moving tiles
			for (int i = 0; i < Game.NUM_TILES; i++)
			{
				Tile tile = game.tiles[i];
				
				if (tile.onTheMove)
				{
					Point pos = getPosition(tile);
					
					if (pos.x - tile.currentX < 50 && pos.y - tile.currentY < 50)
					{
						tile.currentX = pos.x;
						tile.currentY = pos.y;
						tile.onTheMove = false;
						
						if (tile.inPile())
							pileChange = true;
						else
							wordPanelsChange[panelIndex(tile.p)] = true;
					}					
					
					if (Tile.GRAPHICS_ON)
					{
						tile.currentX += (pos.x - tile.currentX) * MOVE_SPEED;
						tile.currentY += (pos.y - tile.currentY) * MOVE_SPEED;
					}
					else
					{
						tile.currentX = pos.x;
						tile.currentY = pos.y;
					}
					
					if (tile.inPile())
						g.drawImage(TileImages.getImage(tile.letter), tile.currentX, tile.currentY, null);
					else
						g.drawImage(TileImages.getSizedImage(tile.letter, wordPanels[panelIndex(tile.p)].realIndex), tile.currentX, tile.currentY, null);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(0);
		} // remove try/catch eventually
	}
	
	void setNumPlayers(int numPlayers)
	{
		wordPanels = new WordPanel[numPlayers];
		for (int i = 0; i < numPlayers; i++)
			wordPanels[i] = new WordPanel(WORD_PANELS[i].width, WORD_PANELS[i].height, (playerIndex + i) % game.numPlayers, game);
		
		wordPanelsChange = new boolean[numPlayers];
	}
	
	void process(int key)
	{
		if (key == KeyEvent.VK_SPACE)
		{
			client.flipTile();
		}
		else if (key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z && word.length() < Game.MAX_LENGTH)
		{
			word.append((char)key);
			inputPanelChange = true;
		}
		else if (key == KeyEvent.VK_BACK_SPACE && word.length() > 0)
		{
			word.deleteCharAt(word.length() - 1);
			inputPanelChange = true;
		}
		else if (key == KeyEvent.VK_ENTER && word.length() > 0)
		{
			client.attemptTake(word + "");
			word.delete(0, word.length());
			inputPanelChange = true;
		}
	}
	
	private void drawPile()
	{
		pile = new BufferedImage(PILE.width, PILE.height, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics g = pile.createGraphics();
		
		g.setColor(Color.ORANGE);
		g.fillRect(0, 0, PILE.width, PILE.height);
		
		g.setColor(Color.BLACK);
		for (int i = 0; i < 3; i++)
			g.drawRect(i, i, PILE.width - 2 * i - 1, PILE.height - 2 * i - 1);
		
		for (int i = 0; i < Game.NUM_TILES; i++)
			if (game.tiles[i].inPile() && !game.tiles[i].onTheMove)
			{
				if (!game.tiles[i].flipped())
					g.drawImage(TileImages.getBlank(spots[i].degree), spots[i].point.x, spots[i].point.y, null);			
				else if (i == game.lastFlipIndex)
					g.drawImage(TileImages.getHighlightedImage(game.tiles[i].letter, spots[i].degree), spots[i].point.x, spots[i].point.y, null);
				else
					g.drawImage(TileImages.getRotatedImage(game.tiles[i].letter, spots[i].degree), spots[i].point.x, spots[i].point.y, null);
			}
	}
	
	private void drawInputPanel()
	{
		inputPanel = new BufferedImage(INPUT_PANEL.width, INPUT_PANEL.height, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics g = inputPanel.createGraphics();
		
		g.setColor(Color.BLACK);
		for (int i = 0; i < 2; i++)
			g.drawRect(i, i, INPUT_PANEL.width - 2 * i - 1, INPUT_PANEL.height - 2 * i - 1);
		
		g.fillPolygon(new int[] {20, 33, 20}, new int[] {20, 30, 40}, 3);
		
		g.setFont(new Font("Courier New", 0, 48));
		g.drawString(word + "|", 40, 50);
	}
	
	private void setupSpots()
	{
		spots = new TileSpot[Game.NUM_TILES];
		for (int i = 0; i < Game.NUM_TILES; i++)
		{
			int x = 10 + 50 * (i % 14) + (int)(Math.random() * 12);
			int y = 10 + 50 * (i / 14) + (int)(Math.random() * 12);
			spots[i] = new TileSpot(new Point(x, y), (int)(Math.random() * 360));
		}
	}
	
	private Point getPosition(Tile tile)
	{
		if (tile.inPile())
			return new Point(PILE.x + spots[tile.index].point.x, PILE.y + spots[tile.index].point.y);
		else
		{
			int index = panelIndex(tile.p);
			Point pos = wordPanels[index].position(tile.w, tile.c);
			return new Point(WORD_PANELS[index].x + pos.x, WORD_PANELS[index].y + pos.y);
		}
	}
	
	private int panelIndex(int player)
	{
		return (player + game.numPlayers - playerIndex) % game.numPlayers;
	}
}

class WordPanel
{
	static final int MARGIN = 20;
	static final int HORIZ_BUFFER = 24;
	static final int VERT_BUFFER = 8;
	
	final int WIDTH, HEIGHT;
	final int player;
	final Game game;
	BufferedImage image;
	
	int height;
	ArrayList<Integer> cumWidths;
	int tileSize, realIndex;
	
	WordPanel(int WIDTH, int HEIGHT, int player, Game game)
	{
		this.WIDTH = WIDTH;
		this.HEIGHT = HEIGHT;
		this.player = player;
		this.game = game;
	}
	
	void draw()
	{
		image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics g = image.createGraphics();
		
		g.setColor(Color.YELLOW);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		
		g.setColor(Color.BLACK);
		g.drawRect(0, 0, WIDTH - 2, HEIGHT - 2);
		
		g.setFont(new Font("Times New Roman", 0, 14));
		g.drawString(game.playerNames[player] + " - Score: " + game.scores[player] + "", 2, 16);
		
		int tileSizeIndex = 0;
		do
		{
			tileSize = (int)(TileImages.TILE_SIZE * (1 - TileImages.SIZE_GAP * tileSizeIndex));
			tileSizeIndex++;
		}
		while (tileSizeIndex < TileImages.NUM_SIZES && !fit());
		realIndex = tileSizeIndex - 1;
		
		for (int i = 0; i < Game.NUM_TILES; i++)
		{
			Tile tile = game.tiles[i];
			
			if (tile.p == player && !tile.onTheMove)
			{
				Point pos = position(tile.w, tile.c);
				g.drawImage(TileImages.getSizedImage(tile.letter, realIndex), pos.x, pos.y, null);
			}
		}
	}
	
	Point position(int word, int character)
	{
		int y = MARGIN + (word % height) * (tileSize + VERT_BUFFER);
		int x = MARGIN + cumWidths.get(word / height) * tileSize + HORIZ_BUFFER * (word / height) + tileSize * character;			
		return new Point(x, y);
	}
	
	private boolean fit()
	{
		height = (HEIGHT - 2 * MARGIN + VERT_BUFFER) / (tileSize + VERT_BUFFER);
		
		cumWidths = new ArrayList<Integer>();
		cumWidths.add(0);
		int maxLen, cumLen = 0;
		
		Iterator<Word> it = wordPile().iterator();
		while (it.hasNext())
		{
			maxLen = 0;
			
			for (int i = 0; i < height && it.hasNext(); i++)
			{
				int len = it.next().length();
				
				if (len > maxLen)
					maxLen = len;
			}
			
			cumLen += maxLen;
			cumWidths.add(cumLen);
		}
		
		return cumLen * tileSize + HORIZ_BUFFER * (cumWidths.size() - 1) + 2 * MARGIN < WIDTH;
	}
	
	private LinkedList<Word> wordPile()
	{
		return game.wordPiles[player];
	}
}

class InfoPanel extends JPanel
{
	static final int MESSAGE_TIME = 1000; // in milliseconds
	
	Game game;
	int playerIndex;
	
	String newMessage;
	long lastMessageTime;
	
	InfoPanel(Client client)
	{
		setBackground(Color.YELLOW);
		
		game = client.game;
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		String message;
		if (System.currentTimeMillis() < lastMessageTime + MESSAGE_TIME)
		{
			g.setColor(Color.RED);
			message = newMessage;
		}
		else
		{
			g.setColor(Color.BLACK);
			
			if (game.started)
			{
				message = (game.currentPlayer == playerIndex ? "your turn" : game.playerNames[game.currentPlayer] + "'s turn");
				
				if (game.lastFlipTime > 0)
					message += " - flip in " + game.remainingTime() + " seconds.";
			}
			else
			{
				message = "Game not started yet.";
			}
		}
		
		g.setFont(new Font("Comic Sans", 0, 24));
		g.drawString(message, 10, 27);
	}
}

class Chatbox extends JInternalFrame
{
	static final int WIDTH = 400;
	static final int HEIGHT = 400;
	
	Client client;
	
	JScrollPane pane;
	JTextArea area;
	JTextField field;
	
	Chatbox(Client client)
	{
		super("- SPEED SCRABBLE CHAT -", false, true);		
		this.client = client;
		setupFrame();
	}
	
	void appendLine(String name, String message)
	{
		area.append(name + ": " + message + "\n");
		
		JScrollBar bar = pane.getVerticalScrollBar();
		bar.setValue(bar.getMaximum());
	}
	
	private void setupFrame()
	{
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		
		setLayout(null);
		setSize(WIDTH, HEIGHT);
		setLocation((d.width - WIDTH) / 2, (d.height - HEIGHT) / 2);
		
		area = new JTextArea();
		area.setFont(new Font("Times New Roman", 0, 12));
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		pane = new JScrollPane(area);
		add(pane);
		pane.setSize(WIDTH - 20, HEIGHT - 70);
		pane.setLocation(5, 5);
		
		field = new JTextField();
		field.setFont(new Font("Times New Roman", 0, 12));
		field.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{					
				if (field.getText().length() > 0)
				{
					client.sendMessage(field.getText());
					field.setText("");
					field.repaint();
				}
			}
		});
		add(field);
		field.setSize(WIDTH - 20, 20);
		field.setLocation(5, HEIGHT - 60);
	}
}

class Rejectbox extends JInternalFrame
{
	Client client;
	
	ButtonGroup group;
	JRadioButton[] radioButtons;
	JButton enter, cancel;
	int maxIndex;
	
	Rejectbox(Client client)
	{
		super("- SELECT WORD TO REJECT: -", false, false);
		this.client = client;		
		setupFrame();
		reset();
	}
	
	void reset()
	{
		group = new ButtonGroup();
		
		maxIndex = client.game.lastSteals.size() - 1;
		for (int i = 0; i < radioButtons.length; i++)
		{
			if (radioButtons[i] != null)
				remove(radioButtons[i]);
				
			if (maxIndex - i < 0)
				continue;
			
			ChangeData steal = client.game.lastSteals.get(maxIndex - i);
			radioButtons[i] = new JRadioButton(steal.taken.word + " (from "
				+ (steal.stolen == null ? "pile" : steal.stolen.word) + ")");
			add(radioButtons[i]);
			radioButtons[i].setSize(200, 30);
			radioButtons[i].setLocation(20, 20 + 40 * i);
			group.add(radioButtons[i]);
		}
		
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation((d.width - 230) / 2, (d.height - 230) / 2);
	}
	
	private void setupFrame()
	{
		setSize(230, 230);
		setLayout(null);
		
		radioButtons = new JRadioButton[3];
		
		enter = new JButton("OK");
		enter.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				for (int i = 0; i < 3; i++)
					if (radioButtons[i] != null && radioButtons[i].isSelected())
						client.requestRejection(maxIndex - i);
						
				setVisible(false);
			}
		});
		add(enter);
		enter.setLocation(5, 150);
		enter.setSize(100, 30);
		
		cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{		
				setVisible(false);
			}
		});
		add(cancel);
		cancel.setLocation(110, 150);
		cancel.setSize(100, 30);
	}
}

class RejectConfirmBox extends JInternalFrame
{
	Client client;
	Word stolen, taken;
	int index;
	
	JTextArea area;
	JButton yes, no;
	
	RejectConfirmBox(Client client, Word stolen, Word taken, int index)
	{
		super("- Reject Confirm Request -", false, false);
		this.client = client;
		this.stolen = stolen;
		this.taken = taken;
		this.index = index;
		setupFrame();
		reset();
	}
	
	void reset()
	{
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation((d.width - 200) / 2, (d.height - 160) / 2);
	}
	
	private void setupFrame()
	{
		setSize(200, 160);
		setLayout(null);
		
		area = new JTextArea("Request to undo your taking the word " + taken.word + " from " + (stolen == null ? "the pile" : stolen.word) + ".\nDo you agree?");
		area.setFont(new Font("Times New Roman", 0, 12));
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		add(area);
		area.setSize(150, 60);
		area.setLocation(25, 25);
		
		yes = new JButton("YES");
		yes.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				client.requestRejection(index);
				dispose();
			}
		});
		add(yes);
		yes.setSize(75, 30);
		yes.setLocation(25, 90);
		
		no = new JButton("NO");
		no.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		add(no);
		no.setSize(75, 30);
		no.setLocation(100, 90);
	}
}

class TileImages
{
	static final int TILE_SIZE = 36;
	static final int INCREMENT = 30; // must divide 180
	static final int NUM_SIZES = 10;
	static final double SIZE_GAP = .05;
	
	static Image[] blanks = new Image[360 / INCREMENT];
	static Image[] tileImages = new Image[Game.NUM_LETTERS];
	static Image[][] resizedImages = new Image[Game.NUM_LETTERS][NUM_SIZES];
	static Image[][] rotatedImages = new Image[Game.NUM_LETTERS][360 / INCREMENT];
	static Image[][] highlightedImages = new Image[Game.NUM_LETTERS][360 / INCREMENT];
	
	static void load()
	{
		System.out.println("LOADING IMAGES...");
		
		try
		{
			BufferedImage blank = ImageIO.read(new File("blank.bmp"));
			BufferedImage allTiles = ImageIO.read(new File("tiles.bmp"));
			
			fillRotations(blank, blanks);
			
			for (int n = 0; n < Game.NUM_LETTERS; n++)
			{
				BufferedImage image = new BufferedImage(42, 42, BufferedImage.TYPE_3BYTE_BGR);				
				for (int i = 0; i < 42; i++)
					for (int j = 0; j < 42; j++)
						image.setRGB(i, j, allTiles.getRGB(42 * n + i, j));						
				tileImages[n] = image.getScaledInstance(TILE_SIZE, TILE_SIZE, 0);
				
				for (int i = 0; i < NUM_SIZES; i++)
				{
					resizedImages[n][i] = image.getScaledInstance((int)(TILE_SIZE * (1 - SIZE_GAP * i)),
						(int)(TILE_SIZE * (1 - SIZE_GAP * i)), 0);
				}
				
				fillRotations(image, rotatedImages[n]);
				
				BufferedImage highlighted = new BufferedImage(42, 42, BufferedImage.TYPE_3BYTE_BGR);
				for (int i = 0; i < 42; i++)
					for (int j = 0; j < 42; j++)
					{
						if (i <= 2 || i >= 40 || j <= 2 || j >= 40)
							highlighted.setRGB(i, j, Color.RED.getRGB());
						else
							highlighted.setRGB(i, j, image.getRGB(i, j));
					}			
				fillRotations(highlighted, highlightedImages[n]);
			}
		}
		catch (IOException e)
		{
			System.out.println("COULD NOT FIND TILE IMAGES.");
			e.printStackTrace();
		}
	}
	
	static Image getBlank(int degree)
	{
		return blanks[degree / INCREMENT];
	}
	
	static Image getImage(int c)
	{
		return tileImages[c];
	}
	
	static Image getSizedImage(int c, int size)
	{
		return resizedImages[c][size];
	}
	
	static Image getRotatedImage(int c, int degree)
	{
		return rotatedImages[c][degree / INCREMENT];
	}
	
	static Image getHighlightedImage(int c, int degree)
	{
		return highlightedImages[c][degree / INCREMENT];
	}
	
	private TileImages() {}
	
	private static void fillRotations(BufferedImage image, Image[] rotateds)
	{
		BufferedImage rotator = new BufferedImage(63, 63, BufferedImage.TYPE_4BYTE_ABGR);
		for (int i = 0; i < 63; i++)
			for (int j = 0; j < 63; j++)
				rotator.setRGB(i, j, 0x00ffffff);
		for (int i = 0; i < 42; i++)
			for (int j = 0; j < 42; j++)
				rotator.setRGB(i + 10, j + 10, image.getRGB(i, j));
		
		for (int degree = 0; degree < 180; degree += INCREMENT)
		{
			BufferedImage rotated = rotate(rotator, degree);
			rotateds[degree / INCREMENT] = rotated.getScaledInstance(TILE_SIZE*3/2, TILE_SIZE*3/2, 0);
			
			BufferedImage flipped = flip(rotated);
			rotateds[(degree + 180) / INCREMENT] = flipped.getScaledInstance(TILE_SIZE*3/2, TILE_SIZE*3/2, 0);
		}
	}
	
	private static BufferedImage rotate(BufferedImage rotator, int degree)
	{
		BufferedImage rotated = new BufferedImage(63, 63, BufferedImage.TYPE_4BYTE_ABGR);
		double theta = Math.toRadians(degree);
		for (int i = 0; i < 63; i++)
			for (int j = 0; j < 63; j++)
			{
				double norm = Math.hypot(i - 31, j - 31);
				double angle = Math.atan((double)(i - 31) / (j - 31));
				if (j < 31) angle += Math.PI;
				angle += theta;
				
				int i_ = (int)Math.round(norm * Math.sin(angle)) + 31;
				int j_ = (int)Math.round(norm * Math.cos(angle)) + 31;
				
				if (i_ >= 0 && i_ < 63 && j_ >= 0 && j_ < 63)
					rotated.setRGB(i, j, rotator.getRGB(i_, j_));
			}
			
		return rotated;
	}
	
	private static BufferedImage flip(BufferedImage image)
	{
		BufferedImage flipped = new BufferedImage(63, 63, BufferedImage.TYPE_4BYTE_ABGR);
		for (int i = 0; i < 63; i++)
			for (int j = 0; j < 63; j++)
				flipped.setRGB(i, j, image.getRGB(62 - i, 62 - j));
			
		return flipped;
	}
}

class ChangeData
{
	int playerStolen, stolenIndex;
	int playerTaken;
	Word stolen, taken;
	ArrayList<int[]> tileMoves;
	
	ChangeData()
	{
		tileMoves = new ArrayList<int[]>();
		playerStolen = stolenIndex = playerTaken = -1;
	}
	
	public String toString()
	{
		StringBuilder output = new StringBuilder();
		output.append(" " + playerStolen + " " + stolenIndex);
		output.append(" " + playerTaken);
		output.append(" " + (stolen == null ? "$" : stolen.word) + " " + taken.word);
		output.append(" " + tileMoves.size());
		for (int[] move : tileMoves)
			output.append(" " + move[0] + " " + move[1] + " " + move[2] + " " + move[3] + " " + move[4] + " " + move[5] + " " + move[6]);
		return output + "";
	}
	
	static ChangeData parse(String s)
	{
		ChangeData data = new ChangeData();
		StringTokenizer t = new StringTokenizer(s);
		
		data.playerStolen = Integer.parseInt(t.nextToken());
		data.stolenIndex = Integer.parseInt(t.nextToken());
		data.playerTaken = Integer.parseInt(t.nextToken());
		String stolen = t.nextToken();
		data.stolen = stolen.equals("$") ? null : new Word(stolen, data.playerStolen);
		data.taken = new Word(t.nextToken(), data.playerTaken);
		int m = Integer.parseInt(t.nextToken());
		for (int i = 0; i < m; i++)
		{
			int[] move = new int[7];
			for (int j = 0; j < 7; j++)
				move[j] = Integer.parseInt(t.nextToken());
			data.tileMoves.add(move);
		}
		
		return data;
	}
}

class TileSpot
{
	final Point point;
	final int degree;
	
	TileSpot(Point point_, int degree_)
	{
		point = point_;
		degree = degree_;
	}
}

class Tile
{
	static boolean GRAPHICS_ON = false;
	
	final int letter, index;
	int p, w, c;
	int currentX, currentY;
	boolean onTheMove;
	
	Tile(int letter, int index)
	{
		this.letter = letter;
		this.index = index;
		toPile(false);
	}
	
	void flip()
	{
		w = 1;
	}
	
	void toPile(boolean flipped)
	{
		p = -1;
		w = flipped ? 1 : 0;
		onTheMove = true;
	}
	
	void move(int p, int w, int c)
	{
		this.p = p;
		this.w = w;
		this.c = c;
		onTheMove = true;
	}
	
	boolean inPile()
	{
		return p == -1;
	}
	
	boolean flipped()
	{
		return w == 1;
	}
}

class Word
{
	String word;
	int player;
	
	Word(String word_, int player_)
	{
		word = word_;
		player = player_;
	}
	
	int length()
	{
		return word.length();
	}
	
	char charAt(int index)
	{
		return word.charAt(index);
	}
	
	int indexOf(int start, int c)
	{
		return word.indexOf(start, c);
	}
	
	public String toString()
	{
		return word + " " + player;
	}
}

class Server
{
	static int TIE_BUFFER = 200; // in milliseconds
	static boolean COUNT_TIES = false;
	
	boolean open;
	final ServerSocket s;
	ArrayList<PrintWriter> writers;
	ArrayList<BufferedReader> readers;
	int numClients;
	
	ActiveGame game;
	boolean[] rejectionRequested;
	
	ArrayList<Take> takeQueue;
	ArrayList<String>[] formerTakes;
	boolean[] ableToTake;
	
	Timer flipper, queueListener;
	
	Server(int port) throws IOException
	{
		open = true;
		s = new ServerSocket(port);
		writers = new ArrayList<PrintWriter>();
		readers = new ArrayList<BufferedReader>();
		numClients = 0;
		
		game = new ActiveGame();
		rejectionRequested = new boolean[Game.NUM_TILES];
	}
	
	void initiate(int numPlayers) throws IOException
	{
		String[] playerNames = new String[numPlayers];
		
		while (numClients < numPlayers)
		{
			String name = receive();
			
			if (name != null)
				playerNames[numClients - 1] = name;
		}
			
		newGame(numPlayers, playerNames);
		startListeners();
		startAutoFlipper();
		startQueueListener();
	}
	
	String receive() throws IOException
	{
		Socket incoming = s.accept();
		
		PrintWriter writer = new PrintWriter(incoming.getOutputStream(), true);
		BufferedReader reader = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
		writers.add(writer);
		readers.add(reader);
		
		writer.println("WELCOME " + numClients);
		
		String[] codes = getCodes(numClients);
		// NEW_CLIENT [name]
		if (!codes[0].equals("NEW_CLIENT"))
		{
			incoming.close();
			return null;
		}
		numClients++;
		return codes[1].replace('\0', ' ');
	}
	
	void close() throws IOException
	{
		if (flipper != null)
			flipper.stop();
		if (queueListener != null)
			queueListener.stop();
		
		for (PrintWriter writer : writers)
			writer.close();
		for (BufferedReader reader : readers)
			reader.close();
			
		open = false;
		s.close();
	}
	
	boolean isOpen()
	{
		return open;
	}
	
	private void newGame(int numPlayers, String[] playerNames)
	{
		int[] letters = game.newGame(numPlayers, playerNames);
		for (int i = 0; i < rejectionRequested.length; i++)
			rejectionRequested[i] = false;
		
		takeQueue = new ArrayList<Take>();
		formerTakes = (ArrayList<String>[])new ArrayList[game.numPlayers];
		for (int i = 0; i < game.numPlayers; i++)
			formerTakes[i] = new ArrayList<String>();
		ableToTake = new boolean[game.numPlayers];
		
		StringBuilder output = new StringBuilder();
		output.append("NEW " + game.numPlayers);
		for (String s : game.playerNames)
			output.append(" " + s.replace(' ', '\0'));
		for (int i : letters)
			output.append(" " + i);
		announce(output.toString());
	}
	
	private void startListeners()
	{
		for (int i = 0; i < numClients; i++)
			new Listener(i).start();
	}
	
	private void startAutoFlipper()
	{
		flipper = new Timer(1000, new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (game.lastFlipTime > 0 && game.remainingTime() <= 0 && !game.randomIndeces.isEmpty())
					flipTile();
			}
		});
		
		flipper.start();
	}
	
	private void startQueueListener()
	{
		queueListener = new Timer(50, new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (COUNT_TIES)
				{
					long time = System.currentTimeMillis();
					if (!takeQueue.isEmpty() && time >= takeQueue.get(0).time + TIE_BUFFER)
					{
						long endTime = takeQueue.get(0).time + TIE_BUFFER;
						
						ArrayList<Take> currentTakes = new ArrayList<Take>();
						boolean[] takingPlayers = new boolean[game.numPlayers];
						int i = 0;
						while (i < takeQueue.size() && takeQueue.get(i).time <= endTime)
						{
							Take take = takeQueue.get(i);
							
							if (takingPlayers[take.player])
								i++;
							else
							{
								if (!ableToTake[take.player])
									sendMessage("You are temporarily not allowed to steal.", take.player);
								else if (game.canTake(takeQueue.get(i).word))
								{
									if (formerTakes[take.player].contains(take.word))
										sendMessage("You already entered this word!", take.player);
									else
									{
										int j = 0;
										boolean considering = true;
										while (j < currentTakes.size() && considering)
										{
											if (currentTakes.get(j).length() < take.length() && ActiveGame.overtakes(currentTakes.get(j).word, take.word))								
												currentTakes.remove(j);
											else if (currentTakes.get(j).length() > take.length() && ActiveGame.overtakes(take.word, currentTakes.get(j).word))								
												considering = false;
											else
												j++;
										}
										
										if (considering)
										{
											formerTakes[take.player].add(take.word);
											currentTakes.add(take);
										}
									}
								}
								
								takeQueue.remove(i);
								takingPlayers[take.player] = true;
							}
						}
						
						if (currentTakes.size() == 1)
						{
							ChangeData data = game.steal(currentTakes.get(0).word, currentTakes.get(0).player);
							if (data != Game.NO_WORD)
								announce("TAKE " + data.toString().replace(' ', '\0'));
							resetTakers();
						}
						else if (currentTakes.size() > 1)
						{
							for (int j = 0; j < game.numPlayers; j++)
								ableToTake[j] = false;
							for (Take take : currentTakes)
								ableToTake[take.player] = true;
								
							for (Take take : currentTakes)
								sendMessage("TIE! (" + take.word + ")", take.player);
						}
					}
				}
				else
				{
					if (!takeQueue.isEmpty())
					{
						Take take = takeQueue.remove(0);
						
						ChangeData data = game.steal(take.word, take.player);
						if (data != Game.NO_WORD)
							announce("TAKE " + data.toString().replace(' ', '\0'));
						resetTakers();
					}
				}
			}
		});
		
		queueListener.start();
	}
	
	private void announce(String message)
	{
		for (int i = 0; i < numClients; i++)
			writers.get(i).println(message);
	}
	
	private void sendMessage(String message, int player)
	{
		writers.get(player).println("SHOW " + message.replace(' ', '\0'));
	}
	
	private void flipTile()
	{
		int tileIndex = game.flipTile();
		announce("FLIP " + tileIndex);
		
		resetTakers();
		
		for (int i = 0; i < game.numPlayers; i++)
		{
			int j = 0;
			while (j < formerTakes[i].size())
			{
				if (formerTakes[i].get(j).indexOf(game.tiles[tileIndex].letter + 'A') == -1)
					j++;
				else
					formerTakes[i].remove(j);
			}
		}
	}
	
	private void resetTakers()
	{
		for (int i = 0; i < game.numPlayers; i++)
			ableToTake[i] = true;
	}
	
	private String[] getCodes(int index) throws IOException
	{
		String line = readers.get(index).readLine();
		return (line == null ? null : line.split(" "));
	}
	
	class Listener extends Thread
	{
		final int player;
		
		Listener(int player)
		{
			this.player = player;
		}
		
		public void run()
		{
			String[] codes;
			
			while (true)
			{
				try
				{
					codes = getCodes(player);
					
					if (codes == null)
						throw new IOException();
					
					if (codes[0].equals("NEW"))
					{
						// NEW
						game.presentPlayers[player] = false;
						
						if (game.allGone())
							newGame(game.numPlayers, game.playerNames);
						else
							announce("SHOW " + "New game requested!".replace(' ', '\0'));
					}
					else if (codes[0].equals("FLIP"))
					{
						// FLIP
						if (player == game.currentPlayer && !game.randomIndeces.isEmpty())
						{
							flipTile();
							game.presentPlayers[player] = true;
						}
					}
					else if (codes[0].equals("TAKE"))
					{
						// TAKE [word]
						game.presentPlayers[player] = true;
						takeQueue.add(new Take(codes[1], player, System.currentTimeMillis()));
						
						//ChangeData data = game.steal(codes[1], player);
						//if (data == Game.NO_WORD)
						//	continue;
						//
						//announce("TAKE " + data.toString().replace(' ', '\0'));
					}
					else if (codes[0].equals("REJECT"))
					{
						// REJECT [ChangeData index]
						int index = Integer.parseInt(codes[1]);
						
						if (game.lastSteals.isEmpty() || index >= game.lastSteals.size()
							|| index < game.lastSteals.size() - 3)
							{
								continue;
							}
							
						int neededPlayer = game.lastSteals.get(index).playerTaken;
						
						if (neededPlayer == player)
						{
							for (int i = index; i < game.lastSteals.size(); i++)
								rejectionRequested[index] = false;
							
							String word = game.lastSteals.get(index).taken.word;
							game.undoChanges(index);
							announce("UNDO " + index + " " + word);
						}
						else if (!rejectionRequested[index])
						{
							rejectionRequested[index] = true;
							writers.get(neededPlayer).println("REJECT_REQ " + index);
						}
					}
					else if (codes[0].equals("MESSAGE"))
					{
						// MESSAGE [player index] [message]
						announce("MESSAGE " + codes[1] + " " + codes[2]);
					}
				}
				catch (IOException e)
				{
					game.presentPlayers[player] = false;
					if (game.allGone())
					{
						try
						{
							close();
						}
						catch (IOException e_)
						{
							JOptionPane.showMessageDialog(null, "Error.");
						}
					}
					return;
				}
			}
		}
	}
}

class ActiveGame extends Game
{
	int[] charCounts;
	LinkedList<Integer>[] currentLetters;
	LinkedList<Word> words;
	int[][][] tilePlacements;
	
	LinkedList<Integer> randomIndeces;
	
	ActiveGame()
	{
		currentLetters = (LinkedList<Integer>[]) new LinkedList[NUM_LETTERS];
		for (int i = 0; i < NUM_LETTERS; i++)
			currentLetters[i] = new LinkedList<Integer>();
			
		words = new LinkedList<Word>();
		
		randomIndeces = new LinkedList<Integer>();
	}
	
	int[] newGame(int numPlayers, String[] playerNames)
	{
		charCounts = new int[NUM_LETTERS];
		for (int i = 0; i < NUM_LETTERS; i++)
			currentLetters[i].clear();
		
		words.clear();
		words.add(EMPTY);
		
		tilePlacements = new int[numPlayers][NUM_TILES / MIN_LENGTH][MAX_LENGTH];
		for (int i = 0; i < numPlayers; i++)
			for (int j = 0; j < NUM_TILES / MIN_LENGTH; j++)
				for (int k = 0; k < MAX_LENGTH; k++)
					tilePlacements[i][j][k] = -1;
		
		randomIndeces.clear();
		for (int i = 0; i < NUM_TILES; i++)
			randomIndeces.add(i);
		Collections.shuffle(randomIndeces);
		
		ArrayList<Integer> randomLetters = new ArrayList<Integer>();
		for (int i = 0; i < NUM_LETTERS; i++)
			for (int j = 0; j < FREQUENCIES[i]; j++)
				randomLetters.add(i);
		Collections.shuffle(randomLetters);
		
		int[] letters = new int[NUM_TILES];
		for (int i = 0; i < NUM_TILES; i++)
			letters[i] = randomLetters.get(i);
		
		super.newGame(numPlayers, playerNames, letters);
		
		return letters;
	}
	
	int flipTile()
	{
		int index = randomIndeces.removeFirst();
		int letter = tiles[index].letter;
		
		super.flipTile(index);
		charCounts[letter]++;
		currentLetters[letter].add(index);
		
		return index;
	}
	
	boolean canTake(String newWord)
	{
		if (newWord.length() < 3)
			return false;
		
		for (Word word : words)
			if (isStealable(newWord, word.word))
				return true;
				
		return false;
	}
	
	static boolean overtakes(String word, String newWord)
	{
		int[] table = new int[NUM_LETTERS];
		
		for (char c : newWord.toCharArray())
			table[c - 'A']++;
		for (char c : word.toCharArray())
			table[c - 'A']--;
			
		for (int i = 0; i < NUM_LETTERS; i++)
			if (table[i] < 0)
				return false;
				
		return true;
	}
	
	ChangeData steal(String newWord, int player)
	{
		if (newWord.length() < 3 || !Dictionary.isWord(newWord))
			return NO_WORD;
		
		Word toSteal = null;
		int maxScoreDiff = -1;
		
		for (Word word : words)
		{
			if (isStealable(newWord, word.word))
			{
				// checks to see if this steal is better
				int scoreDiff = word.player == player ? newWord.length() - word.length() : newWord.length();
				if (scoreDiff > maxScoreDiff)
				{
					toSteal = word;
					maxScoreDiff = scoreDiff;
				}
				else if (scoreDiff == maxScoreDiff)
				{
					if (toSteal.player >= 0 && 
						(toSteal.player == player || scores[word.player] > scores[toSteal.player]))
						{
							toSteal = word;
						}
				}
			}
		}
		
		if (toSteal == null)
			return NO_WORD;
		
		ChangeData data = new ChangeData();
		int[] charWatch = new int[NUM_LETTERS];
		
		if (toSteal != EMPTY)
		{
			words.remove(toSteal);
			data.playerStolen = toSteal.player;
			data.stolenIndex = wordPiles[toSteal.player].indexOf(toSteal);
			data.stolen = toSteal;
			
			for (int i = 0; i < data.stolen.length(); i++)
			{
				int tileIndex = tilePlacements[data.playerStolen][data.stolenIndex][i];
				int letter = tiles[tileIndex].letter;
				int movedIndex = newWord.indexOf(letter + 'A', charWatch[letter]);
				charWatch[letter] = movedIndex + 1;
				data.tileMoves.add(new int[] {tileIndex, data.playerStolen, data.stolenIndex, i, player,
					(data.playerStolen == player ? data.stolenIndex : wordPiles[player].size()), movedIndex});
			}
			
			// shifts all tiles after one word is taken
			if (data.playerStolen != player)
				for (int i = data.stolenIndex + 1; i < wordPiles[data.playerStolen].size(); i++)
					for (int j = 0; j < wordPiles[data.playerStolen].get(i).length(); j++)
					{
						int tileIndex = tilePlacements[data.playerStolen][i][j];
						data.tileMoves.add(new int[] {tileIndex, data.playerStolen, i, j, data.playerStolen, i - 1, j});
					}
		}
		
		Word taken = new Word(newWord, player);
		words.add(taken);
		data.playerTaken = player;
		data.taken = taken;
		
		updateCharCount(newWord, toSteal.word, charWatch, data);
		
		for (int[] move : data.tileMoves)
			if (inWordPanel(move[1], move[2], move[3]))
				tilePlacements[move[1]][move[2]][move[3]] = -1;
		for (int[] move : data.tileMoves)
			if (inWordPanel(move[4], move[5], move[6]))
				tilePlacements[move[4]][move[5]][move[6]] = move[0];
		
		processChange(data);

		return data;
	}
	
	void undo(ChangeData lastSteal)
	{
		words.remove(lastSteal.taken);
		if (lastSteal.stolen != null)
			words.add(lastSteal.stolen);
			
		for (int[] move : lastSteal.tileMoves)
			if (inWordPanel(move[4], move[5], move[6]))
				tilePlacements[move[4]][move[5]][move[6]] = -1;
		for (int[] move : lastSteal.tileMoves)
		{
			if (inWordPanel(move[1], move[2], move[3]))
				tilePlacements[move[1]][move[2]][move[3]] = move[0];
			else
				randomIndeces.add((int)(Math.random() * randomIndeces.size()), move[0]);
		}
		
		super.undo(lastSteal);
	}
	
	private boolean isStealable(String newWord, String word)
	{
		if (word.length() >= newWord.length())
			return false;
			
		int[] table = new int[NUM_LETTERS];
		
		for (char c : newWord.toCharArray())
			table[c - 'A']++;
		for (char c : word.toCharArray())
		{
			table[c - 'A']--;
			if (table[c - 'A'] < 0)
				return false;
		}
		for (int i = 0; i < NUM_LETTERS; i++)
			if (table[i] > charCounts[i])
				return false;
				
		return true;
	}
	
	private void updateCharCount(String newWord, String toSteal, int[] charWatch, ChangeData data)
	{
		int[] table = new int[NUM_LETTERS];
		for (char c : newWord.toCharArray())
			table[c - 'A']++;
		for (char c : toSteal.toCharArray())
			table[c - 'A']--;
		for (int i = 0; i < NUM_LETTERS; i++)
		{
			charCounts[i] -= table[i];
			for (int j = 0; j < table[i]; j++)
			{
				int index = currentLetters[i].removeFirst();
				int movedIndex = data.taken.indexOf(i + 'A', charWatch[i]);
				charWatch[i] = movedIndex + 1;
				data.tileMoves.add(new int[] {index, -1, 0, 0, data.playerTaken,
					(data.playerStolen == data.playerTaken ? data.stolenIndex : wordPiles[data.playerTaken].size()), movedIndex});
			}
		}
	}
	
	private boolean inWordPanel(int p, int w, int c)
	{
		return p >= 0 && p < numPlayers && w >= 0 && w < NUM_TILES / MIN_LENGTH && c >= 0 && c < MAX_LENGTH;
	}
}

class Take
{
	String word;
	int player;
	long time;
	
	Take(String word_, int player_, long time_)
	{
		word = word_;
		player = player_;
		time = time_;
	}
	
	int length()
	{
		return word.length();
	}
}

class Dictionary
{
	static HashSet<String> dictionary;
	static ArrayList<String> dictionaryList;
	
	static void load()
	{
		System.out.println("LOADING DICTIONARY...");
		
		try
		{
			Scanner input = new Scanner(new File("dictionary_full.txt"));
			dictionary = new HashSet<String>();
			dictionaryList = new ArrayList<String>();
			while (input.hasNext())
			{
				String word = input.next();
				dictionary.add(word);
				dictionaryList.add(word);
			}
		}
		catch (IOException e)
		{
			System.out.println("DICTIONARY FAILED TO LOAD.");
			e.printStackTrace();
		}
	}
	
	static boolean isWord(String word)
	{
		return dictionary.contains(word);
	}
	
	static String getWord(int index)
	{
		return dictionaryList.get(index);
	}
	
	static int size()
	{
		return dictionary.size();
	}
}

class MainScreen
{
	static final int PORT = 8189;
	static final String VERSION = "3.5";
	
	String name;
	JFrame frame;
	JDesktopPane pane;
	TieBufferSlider slider;
	InstructionsFrame instructionsFrame;
	AboutFrame aboutFrame;
	boolean creating, joining;
	boolean receiving, connecting;
	Server server;
	Client client;
	
	int numPlayers;
	String line;
	
	MainScreen(String name)
	{
		this.name = name;
		pane = new JDesktopPane();
		setupFrame();
		creating = joining = receiving = connecting = false;
	}
	
	void display()
	{
		frame.setVisible(true);
	}
	
	private void setupFrame()
	{
		JMenuItem changeName = changeNameMenu();
		JMenuItem setGraphicsOn = graphicsOnMenu();	
		JMenuItem exit = exitMenu();
		JMenuItem setCountTies = countTiesMenu();
		JMenuItem tieSlider = tieSliderMenu();			
		JMenuItem create = createMenu();			
		JMenuItem join = joinMenu();			
		JMenuItem comp = compMenu();
		JMenuItem close = closeMenu();			
		JMenuItem instructions = instructionsMenu();
		JMenuItem about = aboutMenu();
			
		JMenu optionsMenu = new JMenu("Options");
		optionsMenu.add(changeName);
		optionsMenu.add(setGraphicsOn);
		optionsMenu.add(exit);
		
		JMenu tiesMenu = new JMenu("Ties");
		tiesMenu.add(setCountTies);
		tiesMenu.add(tieSlider);
		
		JMenu networkMenu = new JMenu("Network");
		networkMenu.add(create);
		networkMenu.add(join);
		networkMenu.add(comp);
		networkMenu.add(close);
		
		JMenu helpMenu = new JMenu("Help");
		helpMenu.add(instructions);
		helpMenu.add(about);
		
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(optionsMenu);
		menuBar.add(tiesMenu);
		menuBar.add(networkMenu);
		menuBar.add(helpMenu);
		
		JPanel panel = panel();
		slider = new TieBufferSlider();
		instructionsFrame = new InstructionsFrame();
		aboutFrame = new AboutFrame();
		
		setFrameProperties();
		
		pane.add(menuBar);
		menuBar.setSize(400, 30);
		menuBar.setLocation(0, 0);
		
		pane.add(panel);
		panel.setSize(400, 260);
		panel.setLocation(0, 30);
		
		pane.add(slider);
		
		frame.add(pane);
	}
	
	private JMenuItem changeNameMenu()
	{
		JMenuItem changeName = new JMenuItem("Change name");
		changeName.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					name = JOptionPane.showInputDialog(frame, "Enter new name:");
					frame.setTitle("SPEED SCRABBLE v" + VERSION + " - " + name);
				}
			});
		return changeName;
	}
	
	private JMenuItem graphicsOnMenu()
	{
		final JMenuItem setGraphicsOn = new JCheckBoxMenuItem("Tile Graphics");
		setGraphicsOn.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					Tile.GRAPHICS_ON = setGraphicsOn.isSelected();
				}
			});
		return setGraphicsOn;
	}
	
	private JMenuItem exitMenu()
	{
		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					System.exit(0);
				}
			});
		return exit;
	}
	
	private JMenuItem countTiesMenu()
	{
		final JMenuItem setCountTies = new JCheckBoxMenuItem("Count Ties");
		setCountTies.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					Server.COUNT_TIES = setCountTies.isSelected();
				}
			});
		return setCountTies;
	}
	
	private JMenuItem tieSliderMenu()
	{
		final JMenuItem tieSlider = new JMenuItem("Set tie wait-time");
		tieSlider.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					slider.setLocation(50, 120);
					slider.setVisible(true);
					slider.moveToFront();
				}
			});
		return tieSlider;
	}
	
	private JMenuItem createMenu()
	{
		JMenuItem create = new JMenuItem("Create...");
		create.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if ((server != null && server.isOpen()) || creating)
						return;
					creating = true;
					
					new Thread()
					{
						public void run()
						{
							try
							{
								String line = JOptionPane.showInputDialog(frame, "Enter number of players:");
								if (line == null) return;
								numPlayers = Integer.parseInt(line);
								if (numPlayers <= 0 || numPlayers > Game.MAX_PLAYERS)
									throw new NumberFormatException();
									
								server = new Server(PORT);
								setReceiving(true);
								Timer t = new Timer(100, new ActionListener()
								{
									public void actionPerformed(ActionEvent e)
									{
										frame.repaint();
									}
								});
								t.start();
								server.initiate(numPlayers);
								t.stop();
							}
							catch (NumberFormatException e_)
							{
								JOptionPane.showMessageDialog(frame, "Invalid number of players.");
							}
							catch (IOException e_)
							{
								JOptionPane.showMessageDialog(frame, "Server failed to load.");
							}
							finally
							{
								setReceiving(false);
								creating = false;
							}
						}
					}.start();
				}
			});			
		return create;
	}
	
	private JMenuItem joinMenu()
	{
		JMenuItem join = new JMenuItem("Join...");
		join.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (joining)
						return;
					joining = true;
					
					new Thread()
						{
							public void run()
							{
								try
								{
									line = JOptionPane.showInputDialog(frame, "Enter IP address (e.g. '18.111.80.121'):");
									if (line == null) return;
									String[] data = line.split("\\.");
									if (data.length != 4)
										throw new NumberFormatException();
									byte[] address = new byte[4];
									for (int i = 0; i < 4; i++)
									{
										int num = Integer.parseInt(data[i]);
										if (num < 0 || num >= 256)
											throw new NumberFormatException();
											
										address[i] = (byte)num;
									}
									
									client = new HumanClient(name);
									setConnecting(true);
									final Timer t = new Timer(2000, null);
									t.addActionListener(new ActionListener()
									{
										public void actionPerformed(ActionEvent e)
										{
											frame.repaint();
											if (!client.open)
												t.stop();
										}
									});
									t.start();
									client.connect(address, PORT);
								}
								catch (NumberFormatException e_)
								{
									JOptionPane.showMessageDialog(frame, "Invalid IP address.");
								}
								catch (IOException e_)
								{
									JOptionPane.showMessageDialog(frame, "Failed to connect to server.");
								}
								finally
								{
									setConnecting(false);
									joining = false;
								}
							}
						}.start();
				}
			});
		return join;
	}
	
	private JMenuItem compMenu()
	{
		JMenuItem comp = new JMenuItem("Computer Player");
		comp.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (joining)
						return;
					joining = true;
					
					new Thread()
						{
							public void run()
							{
								try
								{
									line = JOptionPane.showInputDialog(frame, "Enter IP address (e.g. '5.110.47.135'):");
									if (line == null) return;
									String[] data = line.split("\\.");
									if (data.length != 4)
										throw new NumberFormatException();
									byte[] address = new byte[4];
									for (int i = 0; i < 4; i++)
									{
										int num = Integer.parseInt(data[i]);
										if (num < 0 || num >= 256)
											throw new NumberFormatException();
											
										address[i] = (byte)num;
									}
									
									client = new ComputerClient("COMPUTER");
									setConnecting(true);
									final Timer t = new Timer(2000, null);
									t.addActionListener(new ActionListener()
									{
										public void actionPerformed(ActionEvent e)
										{
											frame.repaint();
											if (!client.open)
												t.stop();
										}
									});
									t.start();
									client.connect(address, PORT);
								}
								catch (NumberFormatException e_)
								{
									JOptionPane.showMessageDialog(frame, "Invalid IP address.");
								}
								catch (IOException e_)
								{
									JOptionPane.showMessageDialog(frame, "Failed to connect to server.");
								}
								finally
								{
									joining = false;
									setConnecting(false);
								}
							}
						}.start();
				}
			});
		return comp;
	}
	
	private JMenuItem closeMenu()
	{
		JMenuItem close = new JMenuItem("Close Server");
		close.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (server == null || !server.isOpen())
						return;
					
					try
					{
						server.close();
						frame.repaint();
					}
					catch (IOException e_)
					{
						JOptionPane.showMessageDialog(frame, "Error.");
					}
				}
			});
		return close;
	}
	
	private JMenuItem instructionsMenu()
	{
		JMenuItem instructions = new JMenuItem("Instructions");
		instructions.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				instructionsFrame.setLocation(Math.max(0, frame.getX() - 50), Math.max(0, frame.getY() - 50));
				instructionsFrame.setVisible(true);
			}
		});
		return instructions;
	}
	
	private JMenuItem aboutMenu()
	{
		JMenuItem about = new JMenuItem("About");
		about.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				aboutFrame.setLocation(frame.getX() + 50, frame.getY() + 50);
				aboutFrame.setVisible(true);
			}
		});
		return about;
	}
	
	private JPanel panel()
	{
		JPanel panel = new JPanel()
		{
			public void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				
				g.setColor(Color.BLACK);
					g.setFont(new Font("Times New Roman", 0, 24));
				
				if (receiving)
					g.drawString("Receiving player " + (server.numClients + 1) + " of " + numPlayers + "...", 20, 120);
				else if (server != null && server.open)
					g.drawString("Server running.", 20, 120);
				
				if (connecting)
					g.drawString("Connecting to " + line + " ...", 20, 180);
				else if (client != null && client.open)
					g.drawString("Client running.", 20, 180);
			}
		};
		return panel;
	}
	
	private void setFrameProperties()
	{
		frame = new JFrame("SPEED SCRABBLE v" + VERSION + " - " + name);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 300);
		frame.setLocation(100, 100);
		frame.setResizable(false);
		frame.setLayout(null);
		
		pane.setSize(400, 300);
	}
	
	private void setReceiving(boolean flag)
	{
		receiving = flag;
		frame.repaint();
	}
	
	private void setConnecting(boolean flag)
	{
		connecting = flag;
		frame.repaint();
	}
}

class TieBufferSlider extends JInternalFrame
{
	JSlider slider;
	JTextArea value;
	JButton ok, cancel;
	
	TieBufferSlider()
	{
		super("Set Tie wait-time", false, false);
		
		setSize(300, 150);
		setLayout(null);
		
		slider = new JSlider(50, 500, Server.TIE_BUFFER);
		slider.setSize(250, 40);
		slider.setLocation(25, 0);
		slider.setPaintLabels(true);
		slider.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				value.setText(slider.getValue() + " milliseconds");
			}
		});
		add(slider);
		
		value = new JTextArea(slider.getValue() + " milliseconds");
		value.setSize(100, 20);
		value.setLocation(105, 40);
		value.setEditable(false);
		value.setBackground(Color.LIGHT_GRAY);
		add(value);
		
		ok = new JButton("OK");
		ok.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				Server.TIE_BUFFER = slider.getValue();
				setVisible(false);
			}
		});
		add(ok);
		ok.setSize(100, 30);
		ok.setLocation(50, 70);
		
		cancel = new JButton("CANCEL");
		cancel.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				setVisible(false);
				slider.setValue(Server.TIE_BUFFER);
			}
		});
		add(cancel);
		cancel.setSize(100, 30);
		cancel.setLocation(150, 70);
	}
}

class InstructionsFrame extends JFrame
{
	static final int WIDTH = 500;
	static final int HEIGHT = 600;
	
	JScrollPane pane;
	JTextArea area;
	
	InstructionsFrame()
	{
		super("SPEED SCRABBLE v" + MainScreen.VERSION + " INSTRUCTIONS");
		setupFrame();
		readText();
	}
	
	void setupFrame()
	{
		setLayout(null);
		setResizable(false);
		setSize(WIDTH, HEIGHT);
		
		area = new JTextArea();
		area.setFont(new Font("Times New Roman", 0, 14));
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		
		pane = new JScrollPane(area);
		add(pane);
		pane.setSize(WIDTH - 20, HEIGHT - 70);
		pane.setLocation(5, 5);
	}
	
	void readText()
	{
		try
		{
			BufferedReader input = new BufferedReader(new FileReader("instructions.txt"));
			String temp;
			while ((temp = input.readLine()) != null)
				area.append(temp + "\n");
		}
		catch (IOException e)
		{
			area.setText("Error in reading or missing file \"instructions.txt\"");
		}
	}
}

class AboutFrame extends JFrame
{
	static final int WIDTH = 280;
	static final int HEIGHT = 280;
	
	JPanel panel;
	BufferedImage image;
	
	AboutFrame()
	{
		super("About");
		setupFrame();
		readImage();
	}
	
	void setupFrame()
	{
		setLayout(null);
		setResizable(false);
		setSize(WIDTH, HEIGHT);
		
		panel = new JPanel()
		{
			public void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				g.drawImage(image, 0, 0, null);
			}
		};
		panel.setSize(WIDTH, HEIGHT);
		add(panel);
	}
	
	void readImage()
	{
		try
		{
			image = ImageIO.read(new File("about.jpg"));
		}
		catch (IOException e) {}
	}
}

class SpeedScrabble35
{
	public static void main(String ... bobby) throws Exception
	{
		TileImages.load();
		Dictionary.load();
		
		MainScreen screen = new MainScreen("Kevin");
		screen.display();
	}
}
