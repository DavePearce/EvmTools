/*
 * Copyright 2022 ConsenSys Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software dis-
 * tributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package evmtools.util;

public class Bytecodes {

	// 0s: Stop and Arithmetic Operations
	public static final int STOP = 0x0;
	public static final int ADD = 0x01;
	public static final int MUL = 0x02;
	public static final int SUB = 0x03;
	public static final int DIV = 0x04;
	public static final int SDIV = 0x05;
	public static final int MOD = 0x06;
	public static final int SMOD = 0x07;
	public static final int ADDMOD = 0x08;
	public static final int MULMOD = 0x09;
	public static final int EXP = 0x0a;
	public static final int SIGNEXTEND = 0x0b;
	// 10s: Comparison & Bitwise Logic Operations
	public static final int LT = 0x10;
	public static final int GT = 0x11;
	public static final int SLT = 0x12;
	public static final int SGT = 0x13;
	public static final int EQ = 0x14;
	public static final int ISZERO = 0x15;
	public static final int AND = 0x16;
	public static final int OR = 0x17;
	public static final int XOR = 0x18;
	public static final int NOT = 0x19;
	public static final int BYTE = 0x1a;
	public static final int SHL = 0x1b;
	public static final int SHR = 0x1c;
	public static final int SAR = 0x1d;
	// 20s: SHA3
	public static final int KECCAK256 = 0x20;
	// 30s: Environment Information
	public static final int ADDRESS = 0x30;
	public static final int BALANCE = 0x31;
	public static final int ORIGIN = 0x32;
	public static final int CALLER = 0x33;
	public static final int CALLVALUE = 0x34;
	public static final int CALLDATALOAD = 0x35;
	public static final int CALLDATASIZE = 0x36;
	public static final int CALLDATACOPY = 0x37;
	public static final int CODESIZE = 0x38;
	public static final int CODECOPY = 0x39;
	public static final int GASPRICE = 0x3a;
	public static final int EXTCODESIZE = 0x3b;
	public static final int EXTCODECOPY = 0x3c;
	public static final int RETURNDATASIZE = 0x3d;
	public static final int RETURNDATACOPY = 0x3e;
	public static final int EXTCODEHASH = 0x3f;
	// 40s: Block Information
	public static final int BLOCKHASH = 0x40;
	public static final int COINBASE = 0x41;
	public static final int TIMESTAMP = 0x42;
	public static final int NUMBER = 0x43;
	public static final int DIFFICULTY = 0x44;
	public static final int GASLIMIT = 0x45;
	public static final int CHAINID = 0x46;
	public static final int SELFBALANCE = 0x47;
	// 50s: Stack, Memory Storage and Flow Operations
	public static final int POP = 0x50;
	public static final int MLOAD = 0x51;
	public static final int MSTORE = 0x52;
	public static final int MSTORE8 = 0x53;
	public static final int SLOAD = 0x54;
	public static final int SSTORE = 0x55;
	public static final int JUMP = 0x56;
	public static final int JUMPI = 0x57;
	public static final int PC = 0x58;
	public static final int MSIZE = 0x59;
	public static final int GAS = 0x5a;
	public static final int JUMPDEST = 0x5b;
	// 60s & 70s: Push Operations
	public static final int PUSH1 = 0x60;
	public static final int PUSH2 = 0x61;
	public static final int PUSH3 = 0x62;
	public static final int PUSH4 = 0x63;
	public static final int PUSH5 = 0x64;
	public static final int PUSH6 = 0x65;
	public static final int PUSH7 = 0x66;
	public static final int PUSH8 = 0x67;
	public static final int PUSH9 = 0x68;
	public static final int PUSH10 = 0x69;
	public static final int PUSH11 = 0x6a;
	public static final int PUSH12 = 0x6b;
	public static final int PUSH13 = 0x6c;
	public static final int PUSH14 = 0x6d;
	public static final int PUSH15 = 0x6e;
	public static final int PUSH16 = 0x6f;
	public static final int PUSH17 = 0x70;
	public static final int PUSH18 = 0x71;
	public static final int PUSH19 = 0x72;
	public static final int PUSH20 = 0x73;
	public static final int PUSH21 = 0x74;
	public static final int PUSH22 = 0x75;
	public static final int PUSH23 = 0x76;
	public static final int PUSH24 = 0x77;
	public static final int PUSH25 = 0x78;
	public static final int PUSH26 = 0x79;
	public static final int PUSH27 = 0x7a;
	public static final int PUSH28 = 0x7b;
	public static final int PUSH29 = 0x7c;
	public static final int PUSH30 = 0x7d;
	public static final int PUSH31 = 0x7e;
	public static final int PUSH32 = 0x7f;
	// 80s: Duplication Operations
	public static final int DUP1 = 0x80;
	public static final int DUP2 = 0x81;
	public static final int DUP3 = 0x82;
	public static final int DUP4 = 0x83;
	public static final int DUP5 = 0x84;
	public static final int DUP6 = 0x85;
	public static final int DUP7 = 0x86;
	public static final int DUP8 = 0x87;
	public static final int DUP9 = 0x88;
	public static final int DUP10 = 0x89;
	public static final int DUP11 = 0x8a;
	public static final int DUP12 = 0x8b;
	public static final int DUP13 = 0x8c;
	public static final int DUP14 = 0x8d;
	public static final int DUP15 = 0x8e;
	public static final int DUP16 = 0x8f;
	// 90s: Exchange Operations
	public static final int SWAP1 = 0x90;
	public static final int SWAP2 = 0x91;
	public static final int SWAP3 = 0x92;
	public static final int SWAP4 = 0x93;
	public static final int SWAP5 = 0x94;
	public static final int SWAP6 = 0x95;
	public static final int SWAP7 = 0x96;
	public static final int SWAP8 = 0x97;
	public static final int SWAP9 = 0x98;
	public static final int SWAP10 = 0x99;
	public static final int SWAP11 = 0x9a;
	public static final int SWAP12 = 0x9b;
	public static final int SWAP13 = 0x9c;
	public static final int SWAP14 = 0x9d;
	public static final int SWAP15 = 0x9e;
	public static final int SWAP16 = 0x9f;
	// a0s: Logging Operations
	public static final int LOG0 = 0xa0;
	public static final int LOG1 = 0xa1;
	public static final int LOG2 = 0xa2;
	public static final int LOG3 = 0xa3;
	public static final int LOG4 = 0xa4;
	// f0s: System operations
	public static final int CREATE = 0xf0;
	public static final int CALL = 0xf1;
	public static final int CALLCODE = 0xf2;
	public static final int RETURN = 0xf3;
	public static final int DELEGATECALL = 0xf4;
	public static final int CREATE2 = 0xf5;
	public static final int STATICCALL = 0xfa;
	public static final int REVERT = 0xfd;
	public static final int INVALID = 0xfe;
	public static final int SELFDESTRUCT = 0xff;

	private static final String[] opcodes;

	static {
		opcodes = new String[256];
		opcodes[STOP] = "STOP";
		opcodes[ADD] = "ADD";
		opcodes[MUL] = "MUL";
		opcodes[SUB] = "SUB";
		opcodes[DIV] = "DIV";
		opcodes[SDIV] = "SDIV";
		opcodes[MOD] = "MOD";
		opcodes[SMOD] = "SMOD";
		opcodes[ADDMOD] = "ADDMOD";
		opcodes[MULMOD] = "MULMOD";
		opcodes[EXP] = "EXP";
		opcodes[SIGNEXTEND] = "SIGNEXTEND";
		// 10s: Comparison & Bitwise Logic Operations
		opcodes[LT] = "LT";
		opcodes[GT] = "GT";
		opcodes[SLT] = "SLT";
		opcodes[SGT] = "SGT";
		opcodes[EQ] = "EQ";
		opcodes[ISZERO] = "ISZERO";
		opcodes[AND] = "AND";
		opcodes[OR] = "OR";
		opcodes[XOR] = "XOR";
		opcodes[NOT] = "NOT";
		opcodes[BYTE] = "BYTE";
		opcodes[SHL] = "SHL";
		opcodes[SHR] = "SHR";
		opcodes[SAR] = "SAR";
		// 20s: SHA3
		opcodes[KECCAK256] = "KECCAK256";
		// 30s: Environment Information
		opcodes[ADDRESS] = "ADDRESS";
		opcodes[BALANCE] = "BALANCE";
		opcodes[ORIGIN] = "ORIGIN";
		opcodes[CALLER] = "CALLER";
		opcodes[CALLVALUE] = "CALLVALUE";
		opcodes[CALLDATALOAD] = "CALLDATALOAD";
		opcodes[CALLDATASIZE] = "CALLDATASIZE";
		opcodes[CALLDATACOPY] = "CALLDATACOPY";
		opcodes[CODESIZE] = "CODESIZE";
		opcodes[CODECOPY] = "CODECOPY";
		opcodes[GASPRICE] = "GASPRICE";
		opcodes[EXTCODESIZE] = "EXTCODESIZE";
		opcodes[EXTCODECOPY] = "EXTCODECOPY";
		opcodes[RETURNDATASIZE] = "RETURNDATASIZE";
		opcodes[RETURNDATACOPY] = "RETURNDATACOPY";
		opcodes[EXTCODEHASH] = "EXTCODEHASH";
		// 40s: Block Information
		opcodes[BLOCKHASH] = "BLOCKHASH";
		opcodes[COINBASE] = "COINBASE";
		opcodes[TIMESTAMP] = "TIMESTAMP";
		opcodes[NUMBER] = "NUMBER";
		opcodes[DIFFICULTY] = "DIFFICULTY";
		opcodes[GASLIMIT] = "GASLIMIT";
		opcodes[CHAINID] = "CHAINID";
		opcodes[SELFBALANCE] = "SELFBALANCE";
		// 50s: Stack, Memory Storage and Flow Operations
		opcodes[POP] = "POP";
		opcodes[MLOAD] = "MLOAD";
		opcodes[MSTORE] = "MSTORE";
		opcodes[MSTORE8] = "MSTORE8";
		opcodes[SLOAD] = "SLOAD";
		opcodes[SSTORE] = "SSTORE";
		opcodes[JUMP] = "JUMP";
		opcodes[JUMPI] = "JUMPI";
		opcodes[PC] = "PC";
		opcodes[MSIZE] = "MSIZE";
		opcodes[GAS] = "GAS";
		opcodes[JUMPDEST] = "JUMPDEST";
		// 60s & 70s: Push Operations
		opcodes[PUSH1] = "PUSH1";
		opcodes[PUSH2] = "PUSH2";
		opcodes[PUSH3] = "PUSH3";
		opcodes[PUSH4] = "PUSH4";
		opcodes[PUSH5] = "PUSH5";
		opcodes[PUSH6] = "PUSH6";
		opcodes[PUSH7] = "PUSH7";
		opcodes[PUSH8] = "PUSH8";
		opcodes[PUSH9] = "PUSH9";
		opcodes[PUSH10] = "PUSH10";
		opcodes[PUSH11] = "PUSH11";
		opcodes[PUSH12] = "PUSH12";
		opcodes[PUSH13] = "PUSH13";
		opcodes[PUSH14] = "PUSH14";
		opcodes[PUSH15] = "PUSH15";
		opcodes[PUSH16] = "PUSH16";
		opcodes[PUSH17] = "PUSH17";
		opcodes[PUSH18] = "PUSH18";
		opcodes[PUSH19] = "PUSH19";
		opcodes[PUSH20] = "PUSH20";
		opcodes[PUSH21] = "PUSH21";
		opcodes[PUSH22] = "PUSH22";
		opcodes[PUSH23] = "PUSH23";
		opcodes[PUSH24] = "PUSH24";
		opcodes[PUSH25] = "PUSH25";
		opcodes[PUSH26] = "PUSH26";
		opcodes[PUSH27] = "PUSH27";
		opcodes[PUSH28] = "PUSH28";
		opcodes[PUSH29] = "PUSH29";
		opcodes[PUSH30] = "PUSH30";
		opcodes[PUSH31] = "PUSH31";
		opcodes[PUSH32] = "PUSH32";
		// 80s: Duplication Operations
		opcodes[DUP1] = "DUP1";
		opcodes[DUP2] = "DUP2";
		opcodes[DUP3] = "DUP3";
		opcodes[DUP4] = "DUP4";
		opcodes[DUP5] = "DUP5";
		opcodes[DUP6] = "DUP6";
		opcodes[DUP7] = "DUP7";
		opcodes[DUP8] = "DUP8";
		opcodes[DUP9] = "DUP9";
		opcodes[DUP10] = "DUP10";
		opcodes[DUP11] = "DUP11";
		opcodes[DUP12] = "DUP12";
		opcodes[DUP13] = "DUP13";
		opcodes[DUP14] = "DUP14";
		opcodes[DUP15] = "DUP15";
		opcodes[DUP16] = "DUP16";
		// 90s: Exchange Operations
		opcodes[SWAP1] = "SWAP1";
		opcodes[SWAP2] = "SWAP2";
		opcodes[SWAP3] = "SWAP3";
		opcodes[SWAP4] = "SWAP4";
		opcodes[SWAP5] = "SWAP5";
		opcodes[SWAP6] = "SWAP6";
		opcodes[SWAP7] = "SWAP7";
		opcodes[SWAP8] = "SWAP8";
		opcodes[SWAP9] = "SWAP9";
		opcodes[SWAP10] = "SWAP10";
		opcodes[SWAP11] = "SWAP11";
		opcodes[SWAP12] = "SWAP12";
		opcodes[SWAP13] = "SWAP13";
		opcodes[SWAP14] = "SWAP14";
		opcodes[SWAP15] = "SWAP15";
		opcodes[SWAP16] = "SWAP16";
		// a0s: Logging Operations
		opcodes[LOG0] = "LOG0";
		opcodes[LOG1] = "LOG1";
		opcodes[LOG2] = "LOG2";
		opcodes[LOG3] = "LOG3";
		opcodes[LOG4] = "LOG4";
		// f0s: System operations
		opcodes[CREATE] = "CREATE";
		opcodes[CALL] = "CALL";
		opcodes[CALLCODE] = "CALLCODE";
		opcodes[RETURN] = "RETURN";
		opcodes[DELEGATECALL] = "DELEGATECALL";
		opcodes[CREATE2] = "CREATE2";
		opcodes[STATICCALL] = "STATICCALL";
		opcodes[REVERT] = "REVERT";
		opcodes[INVALID] = "INVALID";
		opcodes[SELFDESTRUCT] = "SELFDESTRUCT";
	}

	/**
	 * Get the bytecode nmemonic.
	 *
	 * @param opcode
	 * @return
	 */
	public static String toString(int opcode) {
		if(opcodes[opcode] == null) {
			throw new IllegalArgumentException("missing bytecode string " + String.format("%02x",opcode));
		}
		return opcodes[opcode];
	}
}
