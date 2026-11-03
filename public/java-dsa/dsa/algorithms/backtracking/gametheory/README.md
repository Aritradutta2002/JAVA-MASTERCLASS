# GAME_THEORY — Backtracking + Minimax (Interview-Ready)

This module brings adversarial backtracking problems into the Backtracking track with practical, interview-ready implementations and templates. Focus is on Minimax, Alpha-Beta pruning, memoization with score-difference DP, and state-compressed search.

Highlights:
- Minimax template with Alpha-Beta pruning
- Score-difference DP template for optimal play
- Bitmask + memoization template for large state spaces
- Five curated problems with code and notes

## Theory & Templates

### 1) Minimax with Alpha-Beta (two-player, zero-sum)
Use when two players alternate turns. Alpha-Beta pruning keeps best guaranteed bounds to prune losing branches early.

```java
// Score convention: positive = good for maximizer, negative = good for minimizer
int minimax(State state, int depth, boolean isMax, int alpha, int beta) {
    if (state.isTerminal() || depth == 0) {
        return state.evaluate(); // heuristic or exact score
    }
    if (isMax) {
        int best = Integer.MIN_VALUE;
        for (Move mv : state.legalMoves()) {
            state.apply(mv);
            best = Math.max(best, minimax(state, depth - 1, false, alpha, beta));
            state.undo(mv);
            alpha = Math.max(alpha, best);
            if (alpha >= beta) break; // beta cut-off
        }
        return best;
    } else {
        int best = Integer.MAX_VALUE;
        for (Move mv : state.legalMoves()) {
            state.apply(mv);
            best = Math.min(best, minimax(state, depth - 1, true, alpha, beta));
            state.undo(mv);
            beta = Math.min(beta, best);
            if (alpha >= beta) break; // alpha cut-off
        }
        return best;
    }
}
```

Tips:
- Use shallow depth with heuristic evaluate in large games.
- Order moves to maximize pruning.
- Cache states with transposition table (Zobrist hashing for boards).

### 2) Score-Difference DP (Predict the Winner pattern)
Convert the two-player game to a single function returning the maximum score difference current player can secure assuming optimal play.

```java
int dfs(int l, int r, int[] nums, Integer[][] memo) {
    if (l == r) return nums[l];
    if (memo[l][r] != null) return memo[l][r];
    int takeLeft  = nums[l] - dfs(l + 1, r, nums, memo);
    int takeRight = nums[r] - dfs(l, r - 1, nums, memo);
    return memo[l][r] = Math.max(takeLeft, takeRight);
}
```

### 3) Bitmask + Memoization (state-compressed search)
Represent used choices as a bitmask; memoize by mask and current total/turn.

```java
boolean canWin(int mask, int remain, int maxN, Boolean[] memo) {
    if (remain <= 0) return false; // current player loses (previous won)
    if (memo[mask] != null) return memo[mask];
    for (int x = 1; x <= maxN; x++) {
        int bit = 1 << (x - 1);
        if ((mask & bit) == 0) {
            if (!canWin(mask | bit, remain - x, maxN, memo)) {
                return memo[mask] = true; // winning move
            }
        }
    }
    return memo[mask] = false;
}
```

## Problem Index

- Tic-Tac-Toe (Minimax with Alpha-Beta) — choose optimal move and detect outcome.
- Predict the Winner — score-difference DP with memoization.
- Can I Win — bitmask backtracking with memoization, pruning by totals.
- Beautiful Arrangement — constraint-based backtracking (count valid permutations).
- Word Break II — DFS backtracking with memoization for sentence reconstruction.

Files added in this folder:
- TicTacToe_Minimax.java
- PredictTheWinner_Leetcode486.java
- CanIWin_Leetcode464.java
- BeautifulArrangement_Leetcode526.java
- WordBreakII_Leetcode140.java

## Complexity & Heuristics

- Minimax: O(b^d) time, O(d) space (plus board) — Alpha-Beta reduces node visits with good ordering.
- Score-Diff DP: O(n^2) time, O(n^2) space.
- Bitmask Search: O(2^N * N) time, O(2^N) space.
- Constraint Backtracking: worst-case factorial; prune early by constraints and ordering.

Heuristics and pruning:
- Immediate win/loss detection short-circuits deep search.
- Order promising moves first to increase cut-offs.
- Use memoization/transposition tables to avoid re-exploration.

## Interview Notes

- Explain optimal substructure and why minimax/DP applies.
- State your score convention clearly; avoid sign mistakes.
- Discuss pruning and caching; quantify expected impact.
- Start with a simple correct version; then add alpha-beta and memo.

## Quick Start

- Read the templates above.
- Open the Java files in this folder and run main() demos to see expected behavior.
- Practice explaining the recursion trees and pruning decisions on small examples.