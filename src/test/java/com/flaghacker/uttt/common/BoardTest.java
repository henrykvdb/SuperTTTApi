package com.flaghacker.uttt.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class BoardTest
{
	private static Random random = new Random(0);
	private Board board;

	public BoardTest(Board board)
	{
		this.board = board;
	}

	@Parameterized.Parameters
	public static List<Board> data()
	{
		List<Board> list = new ArrayList<>();

		for (int i = 0; i < 10; i++)
		{
			Board board = new Board();
			play(board, i);
			list.add(board);
		}

		return list;
	}

	private static void play(Board board, int moveCount)
	{
		for (int i = 0; i < moveCount; i++)
		{
			List<Coord> moves = board.availableMoves();
			board.play(moves.get(random.nextInt(moves.size())), board.nextPlayer());
		}
	}

	@Test
	public void testCopySeparate()
	{
		Board copy = board.copy();
		Board ref = board.copy();

		play(copy, 15);
		assertEquals(board, ref);
		assertNotEquals(ref, copy);
	}

	@Test
	public void testCopyEqualsHashcode()
	{
		Board copy = board.copy();

		assertTrue(copy.equals(board) && board.equals(copy));
		assertEquals(board.hashCode(), copy.hashCode());
	}
}
