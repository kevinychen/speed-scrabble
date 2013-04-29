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