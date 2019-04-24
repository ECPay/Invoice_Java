package ecpay.invoice.integration.ecpayOperator;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ecpay.invoice.integration.domain.AllowanceObj;
import ecpay.invoice.integration.domain.DelayIssueObj;
import ecpay.invoice.integration.domain.InvoiceNotifyObj;
import ecpay.invoice.integration.domain.IssueObj;
import ecpay.invoice.integration.errorMsg.ErrorMessage;
import ecpay.invoice.integration.exception.EcpayException;

public class InvoiceVerifyBase {
	protected String confPath = "/ecpay/invoice/integration/config/ECpayInvoice.xml";
	protected Document doc;
	public InvoiceVerifyBase(){
		URL fileURL = this.getClass().getResource(confPath);
		doc = EcpayFunction.xmlParser(fileURL.toString());
		doc.getDocumentElement().normalize();
	}
	
	protected void requireCheck(String FieldName, String objValue, String require){
		if(require.equals("1") && objValue.isEmpty())
			throw new EcpayException(FieldName+"������");
	}
	
	protected void valueCheck(String type, String objValue, Element ele){
		if(objValue.isEmpty())
			return;
		if(type.equals("String")){
			if(ele.getElementsByTagName("pattern") != null){
				Pattern r = Pattern.compile(ele.getElementsByTagName("pattern").item(0).getTextContent().toString());
				Matcher m = r.matcher(objValue);
				if(!m.find()){
					throw new EcpayException(ele.getAttribute("name")+ErrorMessage.COLUMN_RULE_ERROR);
				}
			}
		} else if(type.equals("Opt")){
			List<String> opt = new ArrayList<String>();
			NodeList n = ele.getElementsByTagName("option");
			for(int i=0; i < n.getLength(); i++){
				opt.add(n.item(i).getTextContent().toString());
			}
			if(!opt.contains(objValue)){
				throw new EcpayException(ele.getAttribute("name")+ErrorMessage.COLUMN_RULE_ERROR);
			}
		} else if(type.equals("Int")){
			String mode = ele.getElementsByTagName("mode").item(0).getTextContent();
			String minimum = ele.getElementsByTagName("minimal").item(0).getTextContent();
			String maximum = ele.getElementsByTagName("maximum").item(0).getTextContent();
			if(objValue.isEmpty()){
				throw new EcpayException(ele.getAttribute("name")+ErrorMessage.CANNOT_BE_EMPTY);
			}
			int value = Integer.valueOf(objValue);
			if(mode.equals("GE") && value < Integer.valueOf(minimum)){
				throw new EcpayException(ele.getAttribute("name")+"����p��"+minimum);
			} else if(mode.equals("LE") && value > Integer.valueOf(maximum)){
				throw new EcpayException(ele.getAttribute("name")+"����j��"+maximum);
			} else if(mode.equals("BETWEEN") && value < Integer.valueOf(minimum) && value > Integer.valueOf(maximum)){
				throw new EcpayException(ele.getAttribute("name")+"��������"+minimum+"�M"+maximum+"����");
			} else if(mode.equals("EXCLUDE") && value >= Integer.valueOf(minimum) && value <= Integer.valueOf(maximum)){
				throw new EcpayException(ele.getAttribute("name")+"�����p��"+minimum+"�Τj��"+maximum);
			}
		} else if(type.equals("DepOpt")){
			// TODO
		} 
	}
	
	public void verifyIssue(IssueObj obj){
		String[] itemParamsList = new String[]{};
		String[] vatParamsList = new String[]{};
		double taxFee = 1.0;
		//1. ���S�����̻ۨݨD
		//b �C�L���O[Print]�� 1 => CustomerName, CustomerAddr
		if(obj.getPrint().equals("1")){
			if(obj.getCustomerName().isEmpty() || obj.getCustomerAddr().isEmpty())
				throw new EcpayException("CustomerName and CustomerAddr cannot be empty when Print is 1.");
			if(!obj.getCarruerType().isEmpty())
				throw new EcpayException("Print cannot be 1 when CarruerType is not empty.");
			if(!obj.getCarruerNum().isEmpty())
				throw new EcpayException("Print cannot be 1 when CarruerNum is not empty.");
		}
		//c CustomerPhone�MCustomerEmail�ܤ֤@�Ӧ���
		if(obj.getCustomerPhone().isEmpty() && obj.getCustomerEmail().isEmpty())
			throw new EcpayException("CustomerPhone and CustomerEmail cannot both be empty.");
		//d �O[TaxType]�� 2 => ClearanceMark = 1 or 2,ItemTaxType ��������
		if(obj.getTaxType().equals("2")){
			if(!obj.getItemRemark().isEmpty())
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount", "ItemRemark"};
			else
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount"};
			vatParamsList = new String[]{"ItemCount", "ItemAmount"};
			if(!obj.getClearanceMark().equals("1") && !obj.getClearanceMark().equals("2"))
				throw new EcpayException("ClearanceMark has to be 1 or 2 when TaxType is 2.");
//			if(!obj.getItemTaxType().isEmpty())
//				throw new EcpayException("ItemTaxType must be empty when TaxType is 2.");
			//��[TaxType]��2�ɬ��s�|�v�Avat��0�ɰӫ~������K�|�A�����A�[�|
			//�Yvat��1�ɰӫ~������t�|�A���A�h�|
			if(obj.getVat().equals("0"))
				taxFee = 1;
			else if(obj.getVat().equals("1"))
				taxFee = 1.05;
		//d.1 [TaxType]�� 1 => ItemTaxType, ClearanceMark ��������
		} else if(obj.getTaxType().equals("1")){
			if(!obj.getItemRemark().isEmpty())
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount", "ItemRemark"};
			else
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount"};
			vatParamsList = new String[]{"ItemCount", "ItemAmount"};
//			if(!obj.getItemTaxType().isEmpty())
//				throw new EcpayException("ItemTaxType must be empty when TaxType is 1.");
			if(!obj.getClearanceMark().isEmpty())
				throw new EcpayException("ClearanceMark must be empty when TaxType is 1.");
			//��[TaxType]��1�ɬ����|�Avat��0�ɰӫ~������K�|�A���A�[�|
			//�Yvat��1�ɰӫ~������t�|�A�����A�[�|
			if(obj.getVat().equals("0"))
				taxFee = 1.05;
			else if(obj.getVat().equals("1"))
				taxFee = 1;
		//d.2 [TaxType]�� 3 => ItemTaxType, ClearanceMark ��������
		} else if(obj.getTaxType().equals("3")){
			if(!obj.getItemRemark().isEmpty())
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount", "ItemRemark"};
			else
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount"};
			vatParamsList = new String[]{"ItemCount", "ItemAmount"};
//			if(!obj.getItemTaxType().isEmpty())
//				throw new EcpayException("ItemTaxType must be empty when TaxType is 3.");
			if(!obj.getClearanceMark().isEmpty())
				throw new EcpayException("ClearanceMark must be empty when TaxType is 3.");
			//��[TaxType]��3�ɬ��K�|�Avat��0�ɰӫ~������K�|�A�����A�[�|
			//�Yvat��1�ɰӫ~������t�|�A���A�h�|
			if(obj.getVat().equals("0"))
				taxFee = 1;
			else if(obj.getVat().equals("1"))
				taxFee = 1.05;
		//d.3 [TaxType]�� 9 => ItemTaxType �������ⶵ�ӫ~�]�t�^�H�W,�B���i����
		} else if(obj.getTaxType().equals("9")){
			if(!obj.getItemRemark().isEmpty())
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount", "ItemRemark", "ItemTaxType"};
			else
				itemParamsList = new String[]{"ItemCount", "ItemWord", "ItemPrice", "ItemAmount", "ItemTaxType"};
			vatParamsList = new String[]{"ItemCount", "ItemAmount", "ItemTaxType"};
			if(!obj.getItemTaxType().contains("|"))
				throw new EcpayException("ItemTaxType must contain at least one |.");
			if(obj.getItemTaxType().isEmpty())
				throw new EcpayException("ItemTaxType cannot be empty when TaxType is 9.");
			//��[ItmeTaxType]�t2�ﶵ����[ClearanceMark]����1��2
			if(obj.getItemTaxType().contains("2"))
				if(!obj.getClearanceMark().equals("1") && !obj.getClearanceMark().equals("2"))
					throw new EcpayException("ClearanceMark must be 1 or 2 when ItemTaxType contains 2.");
		}
		//e �Τ@�s��[CustomerIdentifier]���Ȯ� => CarruerType != 1 or 2 or 3, *Donation = 0, print = 1
		if(!obj.getCustomerIdentifier().isEmpty()){
			if(obj.getCarruerType().equals("1") || obj.getCarruerType().equals("2") || obj.getCarruerType().equals("3"))
				throw new EcpayException("CarruerType cannot be 1 or 2 or 3 when CustomerIdentifier is given");
			if(!obj.getDonation().equals("0") && !obj.getPrint().equals("1"))
				throw new EcpayException("Print must be 1 and Donation must be 0 when CustomerIdentifier is given");
		}
		//f [CarruerType]��'' or 1 �� => CarruerNum = '', [CarruerType]�� 2�A CarruerNum = �T�w���׬� 16 �B�榡�� 2 �X�j�p�g�r���[�W 14 �X�Ʀr�C [CarruerType]�� 3 �A�a�T�w���׬� 8 �B�榡�� 1 �X�׽u�u/�v�[�W�� 7 �X�Ʀr�Τj�p�g�r���զ�
		if(obj.getCarruerType().isEmpty() || obj.getCarruerType().equals("1")){
			if(!obj.getCarruerNum().isEmpty())
				throw new EcpayException("CarruerNum must be empty when CarruerType is empty or 1.");
		} else if(obj.getCarruerType().equals("2")){
			if(!obj.getCustomerID().isEmpty())
				throw new EcpayException("CustomerID must be empty when CarruerType is 2.");
			Pattern r = Pattern.compile("[A-Za-z]{2}[0-9]{14}");
			Matcher CarrNum = r.matcher(obj.getCarruerNum());
			if(!CarrNum.find())
				throw new EcpayException("CarruerNum must contain 2 alphabets and 14 numbers when CarruerType is 2.");
		} else if(obj.getCarruerType().equals("3")){
			if(!obj.getCustomerID().isEmpty())
				throw new EcpayException("CustomerID must be empty when CarruerType is 3.");
			Pattern r = Pattern.compile("^\\/[A-Za-z0-9\\s+-.]{7}$");
			Matcher CarrNum = r.matcher(obj.getCarruerNum());
			if(!CarrNum.find())
				throw new EcpayException("CarruerNum must start with / and followed by 7 characters containing alphabets and digits when CarruerType is 3.");
		} else{
			throw new EcpayException("Unexpected value in CarruerType.");
		}
		//g Donation = 1 => LoveCode���ର��, print = 0
		if(obj.getDonation().equals("1")){
			if(obj.getLoveCode().isEmpty())
				throw new EcpayException("LoveCode cannot be empty when Donation is 1.");
			if(!obj.getPrint().equals("0"))
				throw new EcpayException("Print must be 0 when Donation is 1.");
		} else if(obj.getDonation().equals("0"))
			if(!obj.getLoveCode().isEmpty())
				throw new EcpayException("LoveCode must be empty when Donation is 0.");
		//[vat]��0�� => ItemPrice = ���|, ItemAmount = (ItemPrice * ItemCount) + (ItemPrice * ItemCount * tax(5%))
		//���|�[�|��@�ӫ~�ɪ����|�ˤ��J�a�JItemAmount�A�BItemAmount����SalesAmount
		//���|�[�|�h�˰ӫ~�ɥ���|���[�`�a�JItemAmount�A�BItemAmount�������B�[�`��a�JSalesAmount��|�ˤ��J
		//�ӫ~�����t���޽u => �{���O�h�˰ӫ~ *ItemCount �A *ItemPrice �A *ItemAmount �v�@�κ޽u���ΡA�p��ƶq��P�Ĥ@�Ӥ��
		if(obj.getVat().equals("0")){
			if(!obj.getItemPrice().contains("|")){
				if((Float.parseFloat(obj.getItemAmount())+0.5f) != Float.parseFloat(obj.getItemPrice())*Float.parseFloat(obj.getItemCount())*taxFee)
					throw new EcpayException("ItemPrice * ItemCount + tax != ItemAmount");
				//���ҳ浧�ӫ~�X�p�O�_����o�����B
				if(Integer.parseInt(obj.getSalesAmount()) != Math.round((Float.parseFloat(obj.getItemAmount())+0.5f)))
					throw new EcpayException("ItemAmount is not equal to SalesAmount");
			} else if(obj.getItemPrice().contains("|")){
				int itemCount = obj.getItemPrice().split("\\|").length;
				int paramCount = 0;
				Pattern r = Pattern.compile("(\\|\\||^\\||\\|$)");
				Matcher itCount = r.matcher(obj.getItemCount());
				Matcher itAmount = r.matcher(obj.getItemAmount());
				// check if there's empty value.
				if(itCount.find())
					throw new EcpayException("ItemCount contains empty value.");
				else{
					paramCount = obj.getItemCount().split("\\|").length;
					if(paramCount != itemCount)
						throw new EcpayException("Count of item info ItemCount("+paramCount+") not match item count from ItemPrice("+itemCount+")");
				}
				if(itAmount.find())
					throw new EcpayException("ItemAmount contains empty value.");
				else{
					paramCount = obj.getItemAmount().split("\\|").length;
					if(paramCount != itemCount)
						throw new EcpayException("Count of item info ItemAmount("+paramCount+") not match item count from ItemPrice("+itemCount+")");
				}
				if(vatParamsList.length == 3){
					Matcher itTaxType = r.matcher(obj.getItemTaxType());
					if(itTaxType.find())
						throw new EcpayException("ItemTaxType contains empty value.");
					else{
						paramCount = obj.getItemTaxType().split("\\|").length;
						if(paramCount != itemCount)
							throw new EcpayException("Count of item info ItemTaxType("+paramCount+")not match item count from ItemPrice("+itemCount+")");
					}
				}
				String[] amount = obj.getItemAmount().split("\\|");
				String[] price = obj.getItemPrice().split("\\|");
				String[] count = obj.getItemCount().split("\\|");
				for(int i = 0; i <= itemCount-1; i++){
					if(vatParamsList.length == 3){
						String[] itemTaxArr = obj.getItemTaxType().split("\\|");
						if(itemTaxArr[i].equals("1"))
							taxFee = 1.05;
						else if(itemTaxArr[i].equals("2") || itemTaxArr[i].equals("3"))
							taxFee = 1;
						else
							throw new EcpayException("ItemTaxType cannot be "+itemTaxArr[i]+". Available option: 1, 2, 3.");
					}
					if(Double.parseDouble(amount[i]) != Integer.parseInt(price[i])*Integer.parseInt(count[i])*taxFee)
						throw new EcpayException("ItemPrice * ItemCount + 5% tax != ItemAmount");
					//Verify ItemAmount subtotal equal SalesAmount
					Double tmp = 0.0;
					for(int j = 0; j <= itemCount-1; j++)
						tmp += Double.parseDouble(amount[j]);
					if(Integer.parseInt(obj.getSalesAmount()) != tmp.intValue())
						throw new EcpayException("ItemAmount subtotal is not equal to SalesAmount.");
				}
			}
		}
		// vat��1�� => ItemPrice = �t�|, ItemAmount = ItemPrice * ItemCount
		// �ӫ~�����t���޽u => �{���O�h�˰ӫ~ *ItemCount �A *ItemPrice �A *ItemAmount �v�@�κ޽u���ΡA�p��ƶq��P�Ĥ@�Ӥ��
		// ���|���|��@�ӫ~�ɪ����|�ˤ��J�a�JItemAmount�A�BItemAmount����SalesAmount
		// ���|���|�h�˰ӫ~�ɥ���|���[�`�|�ˤ��J��a�JItemAmount�A�BItemAmount�������B�[�`�ᵥ��SalesAmount
		if(obj.getVat().equals("1")){
			if(!obj.getItemPrice().contains("|")){
				if(Float.parseFloat(obj.getItemAmount()) != Float.parseFloat(obj.getItemPrice())*Integer.parseInt(obj.getItemCount())/taxFee)
					throw new EcpayException("ItemPrice * ItemCount - tax != ItemAmount");
				// ���ҳ浧�ӫ~�X�p�O�_����o�����B
				if(Integer.parseInt(obj.getSalesAmount()) != Math.round(Float.parseFloat(obj.getItemAmount())))
					throw new EcpayException("ItemAmount is not equal to SalesAmount");
			} else if(obj.getItemPrice().contains("|")){
				int itemCount = obj.getItemPrice().split("\\|").length;
				int paramCount = 0;
				Pattern r = Pattern.compile("(\\|\\||^\\||\\|$)");
				Matcher itCount = r.matcher(obj.getItemCount());
				Matcher itAmount = r.matcher(obj.getItemAmount());
				// check if there's empty value.
				if(itCount.find())
					throw new EcpayException("ItemCount contains empty value.");
				else{
					paramCount = obj.getItemCount().split("\\|").length;
					if(paramCount != itemCount)
						throw new EcpayException("Count of item info ItemCount("+paramCount+") not match item count from ItemPrice("+itemCount+")");
				}
				if(itAmount.find())
					throw new EcpayException("ItemAmount contains empty value.");
				else{
					paramCount = obj.getItemAmount().split("\\|").length;
					if(paramCount != itemCount)
						throw new EcpayException("Count of item info ItemAmount("+paramCount+") not match item count from ItemPrice("+itemCount+")");
				}
				if(vatParamsList.length == 3){
					Matcher itTaxType = r.matcher(obj.getItemTaxType());
					if(itTaxType.find())
						throw new EcpayException("ItemTaxType contains empty value.");
					else{
						paramCount = obj.getItemTaxType().split("\\|").length;
						if(paramCount != itemCount)
							throw new EcpayException("Count of item info ItemTaxType("+paramCount+")not match item count from ItemPrice("+itemCount+")");
					}
				}
				String[] amount = obj.getItemAmount().split("\\|");
				String[] price = obj.getItemPrice().split("\\|");
				String[] count = obj.getItemCount().split("\\|");
				for(int i = 0; i <= itemCount-1; i++){
					if(vatParamsList.length == 3){
						String[] itemTaxArr = obj.getItemTaxType().split("\\|");
						if(itemTaxArr[i].equals("1"))
							taxFee = 1;
						else if(itemTaxArr[i].equals("2") || itemTaxArr[i].equals("3"))
							taxFee = 1.05;
						else
							throw new EcpayException("ItemTaxType cannot be "+itemTaxArr[i]+". Available option: 1, 2, 3.");
					}
					if(Double.parseDouble(amount[i]) != Integer.parseInt(price[i])*Integer.parseInt(count[i])/taxFee)
						throw new EcpayException("ItemPrice * ItemCount - 5% tax != ItemAmount");
					//Verify ItemAmount subtotal equal SalesAmount
					Double tmp = 0.0;
					for(int j = 0; j <= itemCount-1; j++)
						tmp += Double.parseDouble(amount[j]);
					if(Integer.parseInt(obj.getSalesAmount()) != tmp.intValue())
						throw new EcpayException("ItemAmount subtotal is not equal to SalesAmount.");
				}
			}
		}
		// 3. ���ӫ~�W�١A�ƶq�A���A����Atax�A�X�p�A�Ƶ����ؼƶq�O�_�@�P�A���O�_����
		if(obj.getItemName().isEmpty() || obj.getItemWord().isEmpty())
			throw new EcpayException("ItemName or ItemWord cannot be empty.");
		// ItemTaxType and ItemRemark�|�]��TaxType and ItemRemark is not empty �s�W��@item_params_list
		// �ӫ~�W�٧t���޽u => �{���O�h�˰ӫ~ *ItemName�A *ItemCount �A*ItemWord�A *ItemPrice�A *ItemAmount�A *ItemTaxType�A *ItemRemark�v�@�κ޽u���ΡA�p��ƶq��P�Ĥ@�Ӥ��
		if(obj.getItemName().contains("|")){
			int itemCount = obj.getItemName().split("\\|").length;
			int paramCount = 0;
			Pattern r = Pattern.compile("(\\|\\||^\\||\\|$)");
			Matcher itCount = r.matcher(obj.getItemCount());
			Matcher itWord = r.matcher(obj.getItemWord());
			Matcher itPrice = r.matcher(obj.getItemPrice());
			Matcher itAmount = r.matcher(obj.getItemAmount());
			if(itCount.find())
				throw new EcpayException("ItemCount contains empty value.");
			else{
				paramCount = obj.getItemCount().split("\\|").length;
				if(paramCount != itemCount)
					throw new EcpayException("Count of item info ItemCount("+paramCount+") not match item count from ItemName("+itemCount+")");
			}
			if(itWord.find())
				throw new EcpayException("ItemWord contains empty value.");
			else{
				paramCount = obj.getItemWord().split("\\|").length;
				if(paramCount != itemCount)
					throw new EcpayException("Count of item info ItemWord("+paramCount+") not match item count from ItemName("+itemCount+")");
			}
			if(itPrice.find())
				throw new EcpayException("ItemPrice contains empty value.");
			else{
				paramCount = obj.getItemPrice().split("\\|").length;
				if(paramCount != itemCount)
					throw new EcpayException("Count of item info ItemPrice("+paramCount+") not match item count from ItemName("+itemCount+")");
			}
			if(itAmount.find())
				throw new EcpayException("ItemAmount contains empty value.");
			else{
				paramCount = obj.getItemAmount().split("\\|").length;
				if(paramCount != itemCount)
					throw new EcpayException("Count of item info ItemAmount("+paramCount+") not match item count from ItemName("+itemCount+")");
			}
			if(Arrays.asList(itemParamsList).contains("ItemRemark")){
				Matcher itRemark = r.matcher(obj.getItemRemark());
				if(itRemark.find())
					throw new EcpayException("ItemRemark contains empty value.");
				else{
					paramCount = obj.getItemRemark().split("\\|").length;
					if(paramCount != itemCount)
						throw new EcpayException("Count of item info ItemRemark("+paramCount+") not match item count from ItemName("+itemCount+")");
				}
			}
			if(Arrays.asList(itemParamsList).contains("ItemTaxType")){
				Matcher itTaxType = r.matcher(obj.getItemTaxType());
				if(itTaxType.find())
					throw new EcpayException("ItemTaxType contains empty value.");
				else{
					paramCount = obj.getItemTaxType().split("\\|").length;
					if(paramCount != itemCount)
						throw new EcpayException("Count of item info ItemTaxType("+paramCount+") not match item count from ItemName("+itemCount+")");
				}
			}
			// �ҵ|���O[TaxType] = 9 �� => ItemTaxType ��t��1,2 3(and at least contains one 1 and other)
			if(obj.getTaxType().equals("9")){
				String[] itemTax = obj.getItemTaxType().split("\\|");
				if(!Arrays.asList(itemTax).contains("1") && !Arrays.asList(itemTax).contains("2") && !Arrays.asList(itemTax).contains("3"))
					throw new EcpayException("Illegal ItemTaxType!");
				if(!Arrays.asList(itemTax).contains("1"))
					throw new EcpayException("ItemTaxType must contain at least one 1 when TaxType is 9.");
				if(!Arrays.asList(itemTax).contains("2") && !Arrays.asList(itemTax).contains("3"))
					throw new EcpayException("ItemTaxType cannot be all 1 when TaxType is 9.");
				if(Arrays.asList(itemTax).contains("2") && Arrays.asList(itemTax).contains("3"))
					throw new EcpayException("ItemTaxType cannot contain 2 and 3 at the same time.");
			}
		} else{
			// �S���޽u => �v�@�ˬd@item_params_list����즳�L�޽u
			if(obj.getItemCount().contains("|"))
				throw new EcpayException("ItemCount contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemWord().contains("|"))
				throw new EcpayException("ItemWord contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemPrice().contains("|"))
				throw new EcpayException("ItemPrice contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemAmount().contains("|"))
				throw new EcpayException("ItemAmount contains pipeline delimiter but there's only one item in param ItemName.");
			if(Arrays.asList(itemParamsList).contains("ItemRemark"))
				if(obj.getItemRemark().contains("|"))
					throw new EcpayException("ItemRemark contains pipeline delimiter but there's only one item in param ItemName.");
			if(Arrays.asList(itemParamsList).contains("ItemTaxType"))
				if(obj.getItemTaxType().contains("|"))
					throw new EcpayException("ItemTaxType contains pipeline delimiter but there's only one item in param ItemName.");
		}
	}
	
	public void verifyDelayIssue(DelayIssueObj obj){
		String[] vatParamsList = new String[]{};
		double taxFee = 1.0;
		// 1. ���S�����Ȭ̻ۨݨD
		// b �C�L���O[Print]�� 1 => CustomerName, CustomerAddr
		if(obj.getPrint().equals("1")){
			if(obj.getCustomerName().isEmpty() || obj.getCustomerAddr().isEmpty())
				throw new EcpayException("CustomeName and CustomeAddr cannot be empty when Print is 1.");
			if(!obj.getCarruerType().isEmpty())
				throw new EcpayException("Print cannot be 1 when CarruerType is not empty.");
			if(!obj.getCarruerNum().isEmpty())
				throw new EcpayException("Print cannot be 1 when CarruerNum is not empty.");
		}
		// c CustomerPhone�MCustomerEmail�ܤ֤@�Ӧ���
		if(obj.getCustomerPhone().isEmpty() && obj.getCustomerEmail().isEmpty())
			throw new EcpayException("CustomerPhone and CustomerEmail cannot both be empty.");
		// d [TaxType]�� 2 => ClearanceMark = ������ 1 or 2,ItemTaxType ��������
		if(obj.getTaxType().equals("2")){
			taxFee = 1.05;
			vatParamsList = new String[]{"ItemCount", "ItemAmount"};
			if(!obj.getClearanceMark().equals("1") && !obj.getClearanceMark().equals("2"))
				throw new EcpayException("ClearanceMark must be 1 or 2 when TaxType is 2.");
//			if(!obj.getItemTaxType().isEmpty())
//				throw new EcpayException("ItemTaxType must be empty when TaxType is 2.");
		// d.1 [TaxType]�� 1 => ItemTaxType, ClearanceMark ��������
		} else if(obj.getTaxType().equals("1")){
			taxFee = 1;
			vatParamsList = new String[]{"ItemCount", "ItemAmount"};
//			if(!obj.getItemTaxType().isEmpty())
//				throw new EcpayException("ItemTaxType must be empty when TaxType is 1.");
			if(!obj.getClearanceMark().isEmpty())
				throw new EcpayException("ClearanceMark must be empty when TaxType is 1.");
		// d.2 [TaxType]�� 3 => ItemTaxType, ClearanceMark ��������
		} else if(obj.getTaxType().equals("3")){
			taxFee = 1.05;
			vatParamsList = new String[]{"ItemCount", "ItemAmount"};
//			if(!obj.getItemTaxType().isEmpty())
//				throw new EcpayException("ItemTaxType must be empty when TaxType is 3.");
			if(!obj.getClearanceMark().isEmpty())
				throw new EcpayException("ClearanceMark must be empty when TaxType is 3.");
		// d.3 [TaxType]�� 9 => ItemTaxType �������ⶵ�ӫ~�]�t�^�H�W,�B���i����
		} else if(obj.getTaxType().equals("9")){
			vatParamsList = new String[]{"ItemCount", "ItemAmount", "ItemTaxType"};
			if(!obj.getItemTaxType().contains("|"))
				throw new EcpayException("ItemTaxType must contain at least one |.");
			if(obj.getItemTaxType().isEmpty())
				throw new EcpayException("ItemTaxType cannot be empty when TaxType is 9.");
		}
		// e �Τ@�s��[CustomerIdentifier]���Ȯ� => CarruerType != 1 or 2, *Donation = 0, print = 1
		if(!obj.getCustomerIdentifier().isEmpty()){
			if(obj.getCarruerType().equals("1") || obj.getCarruerType().equals("2"))
				throw new EcpayException("CarruerType cannot be 1 or 2 when CustomerIdentifier is given.");
			if(!obj.getDonation().equals("0") || !obj.getPrint().equals("1"))
				throw new EcpayException("Print must be 1 and Donation must be 0 when CustomerIdentifier is given.");
		}
		// DelayFlag Rules When [DelayFlag] is '1' the [DelayDay] range be between 1 and 15
		// When [DelayFlag] is '2' the [DelayDay] range be between 0 and 15
		if(obj.getDelayFlag().equals("1")){
			if(Integer.parseInt(obj.getDelayDay()) > 15 || Integer.parseInt(obj.getDelayDay()) < 1)
				throw new EcpayException("DelayDay must be between 1 and 15 when DelayFlag is 1.");
		} else if(obj.getDelayFlag().equals("2")){
			if(Integer.parseInt(obj.getDelayDay()) > 15 || Integer.parseInt(obj.getDelayDay()) < 0)
				throw new EcpayException("DelayDay must be between 0 and 15 when DelayFlag is 2.");
		}
		// [CarruerType]��'' or 1 �� => CarruerNum = '', [CarruerType]�� 2�A CarruerNum = �T�w���׬� 16 �B�榡�� 2 �X�j�p�g�r���[�W 14 �X�Ʀr�C [CarruerType]�� 3 �A�a�T�w���׬� 8 �B�榡�� 1 �X�׽u�u/�v�[�W�� 7 �X�Ʀr�Τj�p�g�r���զ�
		if(obj.getCarruerType().isEmpty() || obj.getCarruerType().equals("1")){
			if(!obj.getCarruerNum().isEmpty())
				throw new EcpayException("CarruerNum must be empty when CarruerType is empty or 1.");
		} else if(obj.getCarruerType().equals("2")){
			if(!obj.getCustomerID().isEmpty())
				throw new EcpayException("CustomerID must be empty when CarruerType is 2.");
			Pattern r = Pattern.compile("[A-Za-z]{2}[0-9]{14}");
			Matcher m = r.matcher(obj.getCarruerNum());
			if(!m.find())
				throw new EcpayException("CarruerNum must be 2 alphabets and 14 numbers when CarruerType is 2.");
		} else if(obj.getCarruerType().equals("3")){
			if(!obj.getCustomerID().isEmpty())
				throw new EcpayException("CustomerID must be empty when CarruerType is 2.");
			Pattern r = Pattern.compile("^\\/[A-Za-z0-9\\s+-.]{7}$");
			Matcher m = r.matcher(obj.getCarruerNum());
			if(!m.find())
				throw new EcpayException("CarruerNum must start with / followed by 7 alphabet and number characters when CarruerType is 3.");
		} else{
			throw new EcpayException("Unexpected value in CarruerType.");
		}
		// Donation = 1 => LoveCode���ର��, print = 0
		if(obj.getDonation().equals("1")){
			if(obj.getLoveCode().isEmpty())
				throw new EcpayException("LoveCode cannot be empty when Donation is 1.");
			if(!obj.getPrint().equals("0"))
				throw new EcpayException("Print must be 0 when Donation is 1.");
		} else if(obj.getDonation().equals("0")){
			if(!obj.getLoveCode().isEmpty())
				throw new EcpayException("LoveCode must be empty when Donation is 0.");
		}
		// �ӫ~�����t���޽u => �{���O�h�˰ӫ~ *ItemCount �A *ItemPrice �A *ItemAmount �v�@�κ޽u���ΡA�p��ƶq��P�Ĥ@�Ӥ��
		if(!obj.getItemPrice().contains("|")){
			if((Float.parseFloat(obj.getItemAmount())) != Float.parseFloat(obj.getItemPrice())*Integer.parseInt(obj.getItemCount())/taxFee)
				throw new EcpayException("ItemPrice * ItemCount - tax != ItemAmount");
			// ���ҳ浧�ӫ~�X�p�O�_����o�����B
			if(Integer.parseInt(obj.getSalesAmount()) != (Math.round((Float.parseFloat(obj.getItemAmount())))))
				throw new EcpayException("ItemAmount is not equal to SalesAmount.");
		} else if(obj.getItemPrice().contains("|")){
			int itemCount = obj.getItemPrice().split("\\|").length;
			int paramCount = 0;
			Pattern r = Pattern.compile("(\\|\\||^\\||\\|$)");
			Matcher itCount = r.matcher(obj.getItemCount());
			Matcher itAmount = r.matcher(obj.getItemAmount());
			// check if there's empty value.
			if(itCount.find())
				throw new EcpayException("ItemCount contains empty value.");
			else{
				paramCount = obj.getItemCount().split("\\|").length;
				if(paramCount != itemCount)
					throw new EcpayException("Count of item info ItemCount("+paramCount+") not match item count from ItemPrice("+itemCount+")");
			}
			if(itAmount.find())
				throw new EcpayException("ItemAmount contains empty value.");
			else{
				paramCount = obj.getItemAmount().split("\\|").length;
				if(paramCount != itemCount)
					throw new EcpayException("Count of item info ItemAmount("+paramCount+") not match item count from ItemPrice("+itemCount+")");
			}
			if(vatParamsList.length == 3){
				Matcher itTaxType = r.matcher(obj.getItemTaxType());
				if(itTaxType.find())
					throw new EcpayException("ItemTaxType contains empty value.");
				else{
					paramCount = obj.getItemTaxType().split("\\|").length;
					if(paramCount != itemCount)
						throw new EcpayException("Count of item info ItemTaxType("+paramCount+")not match item count from ItemPrice("+itemCount+")");
				}
			}
			String[] amount = obj.getItemAmount().split("\\|");
			String[] price = obj.getItemPrice().split("\\|");
			String[] count = obj.getItemCount().split("\\|");
			for(int i = 0; i <= itemCount-1; i++){
				if(vatParamsList.length == 3){
					String[] itemTaxArr = obj.getItemTaxType().split("\\|");
					if(itemTaxArr[i].equals("1"))
						taxFee = 1;
					else if(itemTaxArr[i].equals("2") || itemTaxArr[i].equals("3"))
						taxFee = 1.05;
					else
						throw new EcpayException("ItemTaxType cannot be "+itemTaxArr[i]+". Available option: 1, 2, 3.");
				}
				if((int)(Float.parseFloat(amount[i])+0.5f) != (int)(Integer.parseInt(price[i])*Integer.parseInt(count[i])/taxFee+0.5f))
					throw new EcpayException("ItemPrice * ItemCount - 5% tax != ItemAmount");
				//Verify ItemAmount subtotal equal SalesAmount
				Float tmp = 0.0f;
				for(int j = 0; j <= itemCount-1; j++)
					tmp += Float.parseFloat(amount[j]);
				if(Integer.parseInt(obj.getSalesAmount()) != (int)(tmp+0.5f))
					throw new EcpayException("ItemAmount subtotal is not equal to SalesAmount.");
			}
		}
		// 3. ���ӫ~�W�١A�ƶq�A���A����Atax�A�X�p�A�Ƶ����ؼƶq�O�_�@�P�A���O�_����
		if(obj.getItemName().isEmpty() || obj.getItemWord().isEmpty()){
			throw new EcpayException("ItemName or ItemWord cannot be empty.");
		}
		// �ӫ~�W�٧t���޽u => �{���O�h�˰ӫ~ *ItemName�A *ItemCount �A*ItemWord�A *ItemPrice�A *ItemAmount�v�@�κ޽u���ΡA�p��ƶq��P�Ĥ@�Ӥ��
		if(obj.getItemName().contains("|")){
			int itemName = obj.getItemName().split("\\|").length;
			int paramCount = 0;
			Pattern r = Pattern.compile("(\\|\\||^\\||\\|$)");
			Matcher itCount = r.matcher(obj.getItemCount());
			Matcher itWord = r.matcher(obj.getItemWord());
			Matcher itPrice = r.matcher(obj.getItemPrice());
			Matcher itAmount = r.matcher(obj.getItemAmount());
			// check if there's empty value.
			if(itCount.find())
				throw new EcpayException("ItemCount contains empty value.");
			else{
				paramCount = obj.getItemCount().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemCount("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			if(itWord.find())
				throw new EcpayException("ItemWord contains empty value.");
			else{
				paramCount = obj.getItemWord().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemWord("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			if(itPrice.find())
				throw new EcpayException("ItemPrice contains empty value.");
			else{
				paramCount = obj.getItemPrice().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemPrice("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			if(itAmount.find())
				throw new EcpayException("ItemAmount contains empty value.");
			else{
				paramCount = obj.getItemAmount().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemAmount("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			// �ҵ|���O[TaxType] = 9 �� => ItemTaxType ��t��1,2 3(and at least contains one 1 and other)
			if(obj.getTaxType().equals("9")){
				String[] itemTax = obj.getItemTaxType().split("\\|");
				if(!Arrays.asList(itemTax).contains("1") && !Arrays.asList(itemTax).contains("2") && !Arrays.asList(itemTax).contains("3"))
					throw new EcpayException("Illegal ItemTaxType!");
				if(!Arrays.asList(itemTax).contains("1"))
					throw new EcpayException("ItemTaxType must contain at least one 1 when TaxType is 9.");
				if(!Arrays.asList(itemTax).contains("2") && !Arrays.asList(itemTax).contains("3"))
					throw new EcpayException("ItemTaxType cannot be all 1 when TaxType is 9.");
				if(Arrays.asList(itemTax).contains("2") && Arrays.asList(itemTax).contains("3"))
					throw new EcpayException("ItemTaxType cannot contain 2 and 3 at the same time.");
			}
		} else{
			// �S���޽u => �v�@�ˬd���L�޽u
			if(obj.getItemCount().contains("|"))
				throw new EcpayException("ItemCount contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemWord().contains("|"))
				throw new EcpayException("ItemWord contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemPrice().contains("|"))
				throw new EcpayException("ItemPrice contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemAmount().contains("|"))
				throw new EcpayException("ItemAmount contains pipeline delimiter but there's only one item in param ItemName.");
		}
	}
	
	public void verifyAllowance(AllowanceObj obj){
		// ���S�����Ȭ̻ۨݨD
		// NotifyPhone�MNotifyMail�ܤ֤@�Ӧ���
		if(obj.getAllowanceNotify().equals("S")){
			if(obj.getNotifyPhone().isEmpty())
				throw new EcpayException("NotifyPhone cannot be empty when AllowanceNotify is S.");
		} else if(obj.getAllowanceNotify().equals("E")){
			if(obj.getNotifyMail().isEmpty())
				throw new EcpayException("NotifyEmail cannot be empty when AllowanceNotify is E.");
		} else if(obj.getAllowanceNotify().equals("A")){
			if(obj.getNotifyPhone().isEmpty() || obj.getNotifyMail().isEmpty())
				throw new EcpayException("NotifyPhone and NotifyMail cannot be empty.");
		}
		// �ӫ~�����t���޽u => �{���O�h�˰ӫ~ *ItemCount �A *ItemPrice �A *ItemAmount �v�@�κ޽u���ΡA�p��ƶq��P�Ĥ@�Ӥ��
		// ���ҳ浧ItemAmount = (ItemPrice * ItemCount)
		if(!obj.getItemPrice().contains("|")){
			if(Float.parseFloat(obj.getItemAmount()) != Float.parseFloat(obj.getItemPrice()) * Integer.parseInt(obj.getItemCount()))
				throw new EcpayException("ItemPrice * ItemCount != ItemAmount.");
			// ���ҳ浧�ӫ~�X�p�O�_����o�����B
			if(Integer.parseInt(obj.getAllowanceAmount()) != Math.round(Float.parseFloat(obj.getItemAmount())))
				throw new EcpayException("ItemAmount is not equal to AllowanceAmount.");
		} else if(obj.getItemPrice().contains("|")){
			int itemPrice = obj.getItemPrice().split("\\|").length;
			int paramCount = 0;
			Pattern r = Pattern.compile("(\\|\\||^\\||\\|$)");
			Matcher itCount = r.matcher(obj.getItemCount());
			Matcher itAmount = r.matcher(obj.getItemAmount());
			// check if there's empty value.
			if(itCount.find())
				throw new EcpayException("ItemCount contains empty value.");
			else{
				paramCount = obj.getItemCount().split("\\|").length;
				if(paramCount != itemPrice)
					throw new EcpayException("Count of item info ItemCount("+paramCount+") not match item count from ItemPrice("+itemPrice+")");
			}
			if(itAmount.find())
				throw new EcpayException("ItemAmount contains empty value.");
			else{
				paramCount = obj.getItemAmount().split("\\|").length;
				if(paramCount != itemPrice)
					throw new EcpayException("Count of item info ItemAmount("+paramCount+") not match item count from ItemPrice("+itemPrice+")");
			}
			String[] amount = obj.getItemAmount().split("\\|");
			String[] price = obj.getItemPrice().split("\\|");
			String[] count = obj.getItemCount().split("\\|");
			for(int i = 0; i <= itemPrice-1; i++){
				if(Float.parseFloat(amount[i]) != Float.parseFloat(price[i])*Integer.parseInt(count[i]))
					throw new EcpayException("ItemPrice * ItemCount != ItemAmount");
				// Verify ItemAmount subtotal equal SalesAmount
				float itemPriceSum = 0;
				for(int j = 0; j <= itemPrice-1; j++)
					itemPriceSum += Float.parseFloat(amount[j]);
				if(Integer.parseInt(obj.getAllowanceAmount()) != Math.round(itemPriceSum))
					throw new EcpayException("ItemAmount subtotal is not equal to AllowanceAmount.");
			}
		}
		// ���ӫ~�W�١A�ƶq�A���A����Atax�A�X�p�A�Ƶ����ؼƶq�O�_�@�P�A���O�_����
		if(obj.getItemName().isEmpty() || obj.getItemWord().isEmpty())
			throw new EcpayException("ItemName or ItemWord cannot be empty.");
		// �ӫ~�W�٧t���޽u => �{���O�h�˰ӫ~ *ItemName�A *ItemCount �A*ItemWord�A *ItemPrice�A *ItemAmount�v�@�κ޽u���ΡA�p��ƶq��P�Ĥ@�Ӥ��
		if(obj.getItemName().contains("|")){
			int itemName = obj.getItemName().split("\\|").length;
			int paramCount = 0;
			Pattern r = Pattern.compile("(\\|\\||^\\||\\|$)");
			Matcher itCount = r.matcher(obj.getItemCount());
			Matcher itWord = r.matcher(obj.getItemWord());
			Matcher itPrice = r.matcher(obj.getItemPrice());
			Matcher itAmount = r.matcher(obj.getItemAmount());
			// check if there's empty value.
			if(itCount.find())
				throw new EcpayException("ItemCount contains empty value.");
			else{
				paramCount = obj.getItemCount().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemCount("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			if(itWord.find())
				throw new EcpayException("ItemWord contains empty value.");
			else{
				paramCount = obj.getItemWord().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemWord("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			if(itPrice.find())
				throw new EcpayException("ItemPrice contains empty value.");
			else{
				paramCount = obj.getItemPrice().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemPrice("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			if(itAmount.find())
				throw new EcpayException("ItemAmount contains empty value.");
			else{
				paramCount = obj.getItemAmount().split("\\|").length;
				if(paramCount != itemName)
					throw new EcpayException("Count of item info ItemAmount("+paramCount+") not match item count from ItemName("+itemName+")");
			}
			// ItemTaxType ��t��1, 3
			if(!obj.getItemTaxType().equals("")){
				String[] itemTax = obj.getItemTaxType().split("\\|");
				if(itemTax.length > 0){
					for(int i = 0; i < itemTax.length; i++){
						if(!itemTax[i].equals("1") && !itemTax[i].equals("3")){
							throw new EcpayException("Illegal ItemTaxType!");
						}
					}
				}
			}
		}else{
			// �S���޽u => �v�@�ˬd���L�޽u
			if(obj.getItemCount().contains("|"))
				throw new EcpayException("ItemCount contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemWord().contains("|"))
				throw new EcpayException("ItemWord contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemPrice().contains("|"))
				throw new EcpayException("ItemPrice contains pipeline delimiter but there's only one item in param ItemName.");
			if(obj.getItemAmount().contains("|"))
				throw new EcpayException("ItemAmount contains pipeline delimiter but there's only one item in param ItemName.");
		}
	}
	
	public void verifyNotify(InvoiceNotifyObj obj){
		// ���S�����Ȭ̻ۨݨD
		// a Phone�MNotifyMail�ܤ֤@�Ӧ���
		if(obj.getPhone().isEmpty() && obj.getNotifyMail().isEmpty())
			throw new EcpayException("Phone and NotifyMail cannot both be empty.");
		// b [Notify] is S [Phone] can not be empty or [Notify] is E [NotifyMail] can not be empty
		// If [Notify] is A [Phone] and [NotifyMail] can not both be empty
		if(obj.getNotify().equals("S")){
			if(obj.getPhone().isEmpty())
				throw new EcpayException("Phone cannot be empty when Notify is S.");
		} else if(obj.getNotify().equals("E")){
			if(obj.getNotifyMail().isEmpty())
				throw new EcpayException("NotifyMail cannot be empty when Notify is E.");
		} else if(obj.getNotify().equals("A")){
			if(obj.getPhone().isEmpty() || obj.getNotifyMail().isEmpty())
				throw new EcpayException("Phone and NotifyMail cannot be empty when Notify is A.");
		} else{
			throw new EcpayException("Unexpected value in Notify.");
		}
		// c [InvoiceTag] is I,II,A,AI,AW [InvoiceNo] can not be empty or [InvoiceTag] is A,AI [AllowanceNo] can not be empty
		if(obj.getInvoiceTag().equals("I") || obj.getInvoiceTag().equals("II") || obj.getInvoiceTag().equals("AW")){
			if(obj.getInvoiceNo().isEmpty())
				throw new EcpayException("InvoiceNo cannot be empty when InvoiceTag is I, II or AW.");
		} else if(obj.getInvoiceTag().equals("A") || obj.getInvoiceTag().equals("AI")){
			Pattern r = Pattern.compile("^\\d{16}$");
			Matcher AllowanceNo = r.matcher(obj.getAllowanceNo());
			if(!AllowanceNo.find())
				throw new EcpayException("AllowanceNo must followed by 16 number characters when InvoiceTag is A or AI.");
			if(obj.getInvoiceNo().isEmpty()){
				if(obj.getAllowanceNo().isEmpty())
					throw new EcpayException("InvoiceNo and AllowanceNo cannot be empty when Notify is A or AI.");
				throw new EcpayException("InvoiceNo cannot be empty.");
			}
			if(!obj.getInvoiceNo().isEmpty()){
				if(obj.getAllowanceNo().isEmpty())
					throw new EcpayException("AllowanceNo cannot be empty.");
			}
		} else{
			throw new EcpayException("Unexpected value in InvoiceTag.");
		}
	}
}
