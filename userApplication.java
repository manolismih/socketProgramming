import java.io.*;
import java.net.*;
import javax.sound.sampled.*;

public class userApplication{
	
	private static final byte[] ithakiIP 		= { (byte)155,(byte)207,(byte)18,(byte)208 };
	private static final int clientListeningPort=48011;
	private static final int serverListeningPort=38011;
	private static final String echoDelay		="E0669";
	private static final String echoInstant		="E0000";
	private static final String image			="M6575";
	private static final String audio			="A2680";
	private static final String vehicle			="V9784OBD=01 ";

	private static final int DURATION			=240;
	private static final int MILLI_TIMEOUT		=2000;
	private static final int MAX_PACKET_SIZE	=2048;
	
	private static InetAddress ithakiAddress;
	private static DatagramSocket receiveSocket, sendSocket;
	private static DatagramPacket receivePacket, sendPacket;
	private static byte[] receiveBuffer = new byte[MAX_PACKET_SIZE];
	private static byte[] sendBuffer = new byte[0];
	private static FileOutputStream binaryFout;
	private static PrintStream printFout;
		
	public static void main(String args[]) throws SocketException, UnknownHostException, IOException, InterruptedException, LineUnavailableException
	{
		sendSocket 		= new DatagramSocket();
		receiveSocket 	= new DatagramSocket(clientListeningPort);
		receiveSocket.setSoTimeout(MILLI_TIMEOUT);
		
		ithakiAddress 	= InetAddress.getByAddress(ithakiIP);
		sendPacket 		= new DatagramPacket(sendBuffer, sendBuffer.length, ithakiAddress, serverListeningPort);
		receivePacket	= new DatagramPacket(receiveBuffer,receiveBuffer.length);
		
		responseTime(echoDelay,false); 										//for G1 G5
		responseTime(echoDelay,true);  										//for G2 G6
		responseTime(echoInstant,false);									//for G3 G7
		responseTime(echoInstant,true);										//for G4 G8
		picture(image+"CAM=FIXUDP=1024");									//for E1
		picture(image+"CAM=PTZUDP=1024");									//for E2
		temperature(echoInstant+"T0");
		soundDPCM(audio+"T999",999);										//for G9 G11 G13
		soundAQDPCM(audio+"AQF998",998);									//for G10 G12 G14 G15 G16
		soundAQDPCM(audio+"AQF999",999);									//for G17 G18	
		drone();															//for G19 G20
		car();														
	}
		
////////////////////////////////////////////////////////////////////////
/******************** Assingment module methods ***********************/
	
	private static void responseTime(String request, boolean forThroughput) throws FileNotFoundException, InterruptedException, IOException
	{
		moduleInit("responseTime"+(forThroughput?"_throughput":""),request,".txt");
		long begin = System.nanoTime(); 
		while ((System.nanoTime()-begin)/1e9 < DURATION)
		{
			if (forThroughput) sendSocket.send(sendPacket); //send an extra packet each time
			printFout.println(ping());
			if (!forThroughput) Thread.sleep(1000);
		}
		if (forThroughput) receive(2000000000); //drop extra packets
	}
	
////////////////////////////////////////////////////////////////////////
	
	private static void picture(String request) throws FileNotFoundException, InterruptedException, IOException
	{
		moduleInit("picture",request,".jpeg");
		receive(100).writeTo(binaryFout);
	}
	
////////////////////////////////////////////////////////////////////////
	
	private static void temperature(String request) throws IOException
	{
		moduleInit("temperature",request+Character.forDigit(0,10),".txt");
		printFout.println(receive(1).toString());
		for (int i=1; i<8; i++)
		{
			sendBuffer = (request+Character.forDigit(i,10)).getBytes();
			sendPacket.setData(sendBuffer,0,sendBuffer.length);
			sendSocket.send(sendPacket);
			printFout.println(receive(1).toString());
		}
	}
	
////////////////////////////////////////////////////////////////////////

	private static void soundDPCM(String request, int nPackets) throws FileNotFoundException, LineUnavailableException, IOException
	{
		moduleInit("soundDPCM",request,".txt");
		AudioFormat linearPCM = new AudioFormat(8000,8,1,true,true); 
		SourceDataLine lineOut = AudioSystem.getSourceDataLine(linearPCM);
		lineOut.open(linearPCM,300000);
		byte[] deltas = receive(nPackets).toByteArray();
		
		byte[] miniBuffer = {0,0};
		lineOut.write(miniBuffer,0,1);
		for (int i=0; i<deltas.length; i++)
		{
			byte delta1 = (byte)( (deltas[i]&0xf0)>>>4 );
			byte delta2 = (byte)( deltas[i]&0x0f );
			delta1 -= 8;
			delta2 -= 8;
			printFout.println(delta1);
			printFout.println(delta2);
			miniBuffer[0] = (byte)(miniBuffer[1]+delta1);
			miniBuffer[1] = (byte)(miniBuffer[0]+delta2);
			lineOut.write(miniBuffer,0,2);
		}
		 
		lineOut.start();
		lineOut.drain();
		lineOut.close();
	}
	
////////////////////////////////////////////////////////////////////////

	private static void soundAQDPCM(String request, int nPackets) throws FileNotFoundException, LineUnavailableException, IOException
	{
		moduleInit("soundAQDPCM",request,".txt");
		PrintStream quantizerStatsFout = new PrintStream("soundAQDPCM"+request+"quantizer.txt");
		AudioFormat linearPCM = new AudioFormat(8000,16,1,true,true); 
		SourceDataLine lineOut = AudioSystem.getSourceDataLine(linearPCM);
		lineOut.open(linearPCM,900000);
		byte[] deltas = receive(nPackets).toByteArray();

		byte[] miniBuffer = {0,0,0,0};
		for (int i=0; i<deltas.length; i+=receivePacket.getLength())
		{
			int mean = deltas[i+1]<<8;
			mean |= deltas[i]&0xff;
			int beta = (deltas[i+3]&0xff)<<8;
			beta |= deltas[i+2]&0xff;
			quantizerStatsFout.print(mean);
			quantizerStatsFout.print(' ');
			quantizerStatsFout.println(beta);
						
			for (int j=4; j<receivePacket.getLength(); j++)
			{
				int delta1 = (deltas[i+j]&0xf0)>>>4;
				int delta2 =  deltas[i+j]&0x0f;
				delta1 = (delta1-8)*beta;
				delta2 = (delta2-8)*beta;
				printFout.println(delta1);
				printFout.println(delta2);
				int sample1 = delta1 + mean;
				int sample2 = delta2 + mean;
				sample1 = Math.max (-32768, Math.min(sample1, 32767));
				sample2 = Math.max (-32768, Math.min(sample2, 32767));
				
				miniBuffer[0] = (byte)( (sample1&0xff00)>>>8 );
				miniBuffer[1] = (byte)( (sample1&0x00ff)     );
				miniBuffer[2] = (byte)( (sample2&0xff00)>>>8 );
				miniBuffer[3] = (byte)( (sample2&0x00ff)     );
				lineOut.write(miniBuffer,0,4);
			}
		}
		
		lineOut.start();
		lineOut.drain();
		lineOut.close();
	}
	
////////////////////////////////////////////////////////////////////////

	private static void drone() throws InterruptedException, IOException
	{
		moduleInit("drone","Please start the ithakicopter application manually in parallel...",".txt");
		receiveSocket.close();
		receiveSocket = new DatagramSocket(48038);
		receiveSocket.setSoTimeout(MILLI_TIMEOUT);
		while (!receive(1).toString().contains("ALTITUDE=11")) ; //wait until an altidute of at least 110 is reached
		printFout.println(receive(1).toString());
		System.out.println("Got high altitude measurement!!!");
		while (!receive(1).toString().contains("ALTITUDE=0")) ; //wait until altidute drops below 100
		printFout.println(receive(1).toString());
		System.out.println("Got low altitude measurement!!!");
		System.out.println("You can now close the ithakicopter application");
		Thread.sleep(3000);
		receiveSocket.close();
		receiveSocket = new DatagramSocket(clientListeningPort);
		receiveSocket.setSoTimeout(MILLI_TIMEOUT);
	}

////////////////////////////////////////////////////////////////////////

	private static void car() throws FileNotFoundException, SocketException, IOException, InterruptedException
	{
		moduleInit("car",vehicle,".txt");
		/* We will first sleep for 500ms and then receive every package,
		 * eithout tolerating delays, thus the small timeout. This way,
		 * delayed packages from previous seconds will be handled and dropped.
		 */ 
		receiveSocket.setSoTimeout(50); 
		String pid[] = {"1F", "0F", "11", "0C", "0D", "05"};
		int engineRunTime, intakeAirTemp, throttlePos, rpm, speed, coolantTemp;
		long begin = System.nanoTime(); 
		while ((System.nanoTime()-begin)/1e9 < DURATION)
		{
			long delayBegin = System.nanoTime();
			for (int i=0; i<pid.length; i++)
			{
				sendBuffer = (vehicle+pid[i]).getBytes();
				sendPacket.setData(sendBuffer,0,sendBuffer.length);
				sendSocket.send(sendPacket);
			}
			Thread.sleep(500); //wait for server response
			
			engineRunTime = intakeAirTemp = throttlePos = rpm = speed = coolantTemp = -100;
			for (String response=receive(1).toString(); !response.isEmpty(); response=receive(1).toString())
			{
				String info[] = response.split(" ");
				if 		(info[1].equals("1F")) engineRunTime = 256*Integer.parseInt(info[2],16) +Integer.parseInt(info[3],16);
				else if (info[1].equals("0F")) intakeAirTemp = 	   Integer.parseInt(info[2],16)-40;
				else if (info[1].equals("11")) throttlePos 	 = 100*Integer.parseInt(info[2],16)/255;
				else if (info[1].equals("0C")) rpm			 =  64*Integer.parseInt(info[2],16) +Integer.parseInt(info[3],16)/4;
				else if (info[1].equals("0D")) speed		 = 	   Integer.parseInt(info[2],16);
				else if (info[1].equals("05")) coolantTemp	 =     Integer.parseInt(info[2],16)-40;
			}
			printFout.format("%d %d %d %d %d %d\n",engineRunTime,intakeAirTemp, throttlePos, rpm, speed, coolantTemp);
			long delay = (System.nanoTime()-delayBegin)/(long)1e6;
			Thread.sleep(1000-delay);
		}
		receiveSocket.setSoTimeout(MILLI_TIMEOUT); 
	}

////////////////////////////////////////////////////////////////////////
/*********************** Helping functions ****************************/

	private static void moduleInit(String name, String request, String fileType) throws FileNotFoundException, IOException
	{
		System.out.println("----->"+name+"("+request+")\n");
		if (name.equals("picture")) binaryFout = new FileOutputStream(name+request+fileType);
		else printFout = new PrintStream(name+request+fileType);
		
		sendBuffer = request.getBytes();
		sendPacket.setData(sendBuffer,0,sendBuffer.length);
		if (!name.equals("responseTime") && !name.equals("responseTime_throughput")) sendSocket.send(sendPacket);
	}
		
////////////////////////////////////////////////////////////////////////

	private static ByteArrayOutputStream receive(int nPackets) throws IOException
	{
		ByteArrayOutputStream ret = new ByteArrayOutputStream();
		int fails=0;
		for (int i=0; i<nPackets; i++)
			try 
			{
				receiveSocket.receive(receivePacket);
				ret.write(receiveBuffer,0,receivePacket.getLength());
			}
			catch (SocketTimeoutException x) 
			{
				System.out.println("Socket timeout");
				if(++fails==3) return ret;
			}
		return ret;
	}

////////////////////////////////////////////////////////////////////////
	
	private static double ping() throws IOException
	{
		sendSocket.send(sendPacket);
		long t1 = System.nanoTime();
		try { 
			receiveSocket.receive(receivePacket); 
		}
		catch (SocketTimeoutException x) { 
			System.out.println("Socket timeout");
			return MILLI_TIMEOUT+3000.0;
		}
		long t2 = System.nanoTime();
		return (t2-t1)/1e6;
	}	

////////////////////////////////////////////////////////////////////////

}
