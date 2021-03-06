package gui.transaction;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import qora.crypto.Base58;
import qora.transaction.SellNameTransaction;
import utils.DateTimeFormat;
import utils.MenuPopupUtil;

@SuppressWarnings("serial")
public class SellNameDetailsFrame extends JFrame
{
	public SellNameDetailsFrame(SellNameTransaction nameSale)
	{
		super("Qora - Transaction Details");
		
		//ICON
		List<Image> icons = new ArrayList<Image>();
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon16.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon32.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon64.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon128.png"));
		this.setIconImages(icons);
		
		//CLOSE
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		//LAYOUT
		this.setLayout(new GridBagLayout());
		
		//PADDING
		((JComponent) this.getContentPane()).setBorder(new EmptyBorder(5, 5, 5, 5));
		
		//LABEL GBC
		GridBagConstraints labelGBC = new GridBagConstraints();
		labelGBC.insets = new Insets(0, 5, 5, 0);
		labelGBC.fill = GridBagConstraints.HORIZONTAL;   
		labelGBC.anchor = GridBagConstraints.NORTHWEST;
		labelGBC.weightx = 0;	
		labelGBC.gridx = 0;
		
		//DETAIL GBC
		GridBagConstraints detailGBC = new GridBagConstraints();
		detailGBC.insets = new Insets(0, 5, 5, 0);
		detailGBC.fill = GridBagConstraints.HORIZONTAL;  
		detailGBC.anchor = GridBagConstraints.NORTHWEST;
		detailGBC.weightx = 1;	
		detailGBC.gridwidth = 2;
		detailGBC.gridx = 1;		
		
		//LABEL TYPE
		labelGBC.gridy = 0;
		JLabel typeLabel = new JLabel("Type:");
		this.add(typeLabel, labelGBC);
						
		//TYPE
		detailGBC.gridy = 0;
		JLabel type = new JLabel("Sell Name Transaction");
		this.add(type, detailGBC);
		
		//LABEL SIGNATURE
		labelGBC.gridy = 1;
		JLabel signatureLabel = new JLabel("Signature:");
		this.add(signatureLabel, labelGBC);
				
		//SIGNATURE
		detailGBC.gridy = 1;
		JTextField signature = new JTextField(Base58.encode(nameSale.getSignature()));
		signature.setEditable(false);
		MenuPopupUtil.installContextMenu(signature);
		this.add(signature, detailGBC);
		
		//LABEL REFERENCE
		labelGBC.gridy = 2;
		JLabel referenceLabel = new JLabel("Reference:");
		this.add(referenceLabel, labelGBC);
						
		//REFERENCE
		detailGBC.gridy = 2;
		JTextField reference = new JTextField(Base58.encode(nameSale.getReference()));
		reference.setEditable(false);
		MenuPopupUtil.installContextMenu(reference);
		this.add(reference, detailGBC);
		
		//LABEL TIMESTAMP
		labelGBC.gridy = 3;
		JLabel timestampLabel = new JLabel("Timestamp:");
		this.add(timestampLabel, labelGBC);
						
		//TIMESTAMP
		detailGBC.gridy = 3;
		JTextField timestamp = new JTextField(DateTimeFormat.timestamptoString(nameSale.getTimestamp()));
		timestamp.setEditable(false);
		MenuPopupUtil.installContextMenu(timestamp);
		this.add(timestamp, detailGBC);
		
		//LABEL REGISTRANT
		labelGBC.gridy = 4;
		JLabel registrantLabel = new JLabel("Seller:");
		this.add(registrantLabel, labelGBC);
		
		//REGISTRANT
		detailGBC.gridy = 4;
		JTextField registrant = new JTextField(nameSale.getOwner().getAddress());
		registrant.setEditable(false);
		MenuPopupUtil.installContextMenu(registrant);
		this.add(registrant, detailGBC);
		
		//LABEL NAME
		labelGBC.gridy = 5;
		JLabel nameLabel = new JLabel("Name:");
		this.add(nameLabel, labelGBC);
		
		//NAME
		detailGBC.gridy = 5;
		JTextField name = new JTextField(nameSale.getNameSale().getKey());
		name.setEditable(false);
		MenuPopupUtil.installContextMenu(name);
		this.add(name, detailGBC);		
		
		//LABEL PRICE
		labelGBC.gridy = 6;
		JLabel priceLabel = new JLabel("Price:");
		this.add(priceLabel, labelGBC);
				
		//PRICE
		detailGBC.gridy = 6;
		JTextField price = new JTextField(nameSale.getNameSale().getAmount().toPlainString());
		price.setEditable(false);
		MenuPopupUtil.installContextMenu(price);
		this.add(price, detailGBC);		
		
		//LABEL FEE
		labelGBC.gridy = 7;
		JLabel feeLabel = new JLabel("Fee:");
		this.add(feeLabel, labelGBC);
						
		//FEE
		detailGBC.gridy = 7;
		JTextField fee = new JTextField(nameSale.getFee().toPlainString());
		fee.setEditable(false);
		MenuPopupUtil.installContextMenu(fee);
		this.add(fee, detailGBC);	
		
		//LABEL CONFIRMATIONS
		labelGBC.gridy = 8;
		JLabel confirmationsLabel = new JLabel("Confirmations:");
		this.add(confirmationsLabel, labelGBC);
								
		//CONFIRMATIONS
		detailGBC.gridy = 8;
		JLabel confirmations = new JLabel(String.valueOf(nameSale.getConfirmations()));
		this.add(confirmations, detailGBC);	
		           
        //PACK
		this.pack();
        this.setResizable(false);
        this.setLocationRelativeTo(null);
        this.setVisible(true);
	}
}
