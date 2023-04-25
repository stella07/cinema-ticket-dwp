package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */
    private SeatReservationService reservationService = new SeatReservationServiceImpl();
    private TicketPaymentService ticketPaymentService = new TicketPaymentServiceImpl();
    private int totalSeatsToAllocate = 0;

    private static final int ADULT_PRICE = 20;

    private static final int CHILD_PRICE = 10;

    private static final int MAX_LIMIT=20;


    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validateInputRequest(ticketTypeRequests);
        Map<TicketTypeRequest.Type, List<TicketTypeRequest>> groupRequestByTicketTypeMap = groupTicketByTicketType(ticketTypeRequests);
        if (checkMaxNoOfSeatsExceeds(groupRequestByTicketTypeMap)) {
            if (isAdultExistsInRequest(groupRequestByTicketTypeMap)) {
                int totalAmountToPay = calculateAmountToPay(groupRequestByTicketTypeMap);
                if (accountId <= 0) {
                    throw new InvalidPurchaseException("Invalid account number");
                } else {
                    ticketPaymentService.makePayment(accountId, totalAmountToPay);
                    reservationService.reserveSeat(accountId, totalSeatsToAllocate);
                }
            }
        }
    }

    /**
     * Validate Input request values
     * @param ticketTypeRequests -array of ticketTypeRequests
     */
    private void validateInputRequest(TicketTypeRequest[] ticketTypeRequests) {
        StringBuilder type= new StringBuilder();
        for(TicketTypeRequest eachRequest:ticketTypeRequests){
            if(eachRequest.getNoOfTickets()<0){
                type.append(eachRequest.getTicketType());
                type.append(",");
            }
        }
        if(!type.toString().isEmpty()){
            throw new InvalidPurchaseException("Please provide valid input for type:"+type.substring(0,type.length()-1));
        }
    }

    /**
     * Method to calculate the amount for payment
     *
     * @param groupRequestByTicketTypeMap - formattedTicketByGroup
     * @return calculatedAmount
     */
    private int calculateAmountToPay(Map<TicketTypeRequest.Type, List<TicketTypeRequest>> groupRequestByTicketTypeMap) {
        int totalAmount = 0;

        for (Map.Entry<TicketTypeRequest.Type, List<TicketTypeRequest>> listEntry : groupRequestByTicketTypeMap.entrySet()) {
            if (listEntry.getKey() == TicketTypeRequest.Type.ADULT) {
                for (TicketTypeRequest ticketTypeRequest : listEntry.getValue()) {
                    totalAmount += ticketTypeRequest.getNoOfTickets() * ADULT_PRICE;
                }
            } else if (listEntry.getKey() == TicketTypeRequest.Type.CHILD) {
                for (TicketTypeRequest ticketTypeRequest : listEntry.getValue()) {
                    totalAmount += ticketTypeRequest.getNoOfTickets() * CHILD_PRICE;
                }
            }
        }
        return totalAmount;
    }

    /**
     * Method to check the max nos of seats exceeds or not
     *
     * @param groupRequestByTicketTypeMap - formattedTicketByGroup
     * @return checkFlag
     */
    private boolean checkMaxNoOfSeatsExceeds(Map<TicketTypeRequest.Type, List<TicketTypeRequest>> groupRequestByTicketTypeMap) {
        for (Map.Entry<TicketTypeRequest.Type, List<TicketTypeRequest>> listEntry : groupRequestByTicketTypeMap.entrySet()) {
            if (listEntry.getKey() == TicketTypeRequest.Type.ADULT || listEntry.getKey() == TicketTypeRequest.Type.CHILD  ) {
                for (TicketTypeRequest ticketTypeRequest : listEntry.getValue()) {
                    if(ticketTypeRequest.getNoOfTickets()>0){
                        totalSeatsToAllocate += ticketTypeRequest.getNoOfTickets();
                    }
                }
            }
        }
        if (totalSeatsToAllocate > MAX_LIMIT) {
            throw new InvalidPurchaseException("Maximum number of tickets allowed is "+ MAX_LIMIT + " but exceeds by "+ (totalSeatsToAllocate - MAX_LIMIT));
        } else {
            return true;
        }
    }

    /**
     * Method to check alteast atleast one adult exists in the list
     *
     * @param groupByTicketType - formattedTicketByGroup
     * @return adultExistsFlag
     * @throws InvalidPurchaseException-
     */
    private boolean isAdultExistsInRequest(Map<TicketTypeRequest.Type, List<TicketTypeRequest>> groupByTicketType) throws InvalidPurchaseException {
        int adultCount = 0; int childCount = 0; int infantCount = 0;
        boolean isChildTicketFlag=false; boolean isInfantTicketFlag=false; boolean isAdultTicketFlag=false;

        for (Map.Entry<TicketTypeRequest.Type, List<TicketTypeRequest>> listEntry : groupByTicketType.entrySet()) {
            if (listEntry.getKey() == TicketTypeRequest.Type.ADULT) {
                for (TicketTypeRequest ticketTypeRequest : listEntry.getValue()) {
                    adultCount += ticketTypeRequest.getNoOfTickets();
                    isAdultTicketFlag=true;
                }
            } else if (listEntry.getKey() == TicketTypeRequest.Type.CHILD) {
                for (TicketTypeRequest ticketTypeRequest : listEntry.getValue()) {
                    childCount += ticketTypeRequest.getNoOfTickets();
                    isChildTicketFlag=true;
                }
            } else if (listEntry.getKey() == TicketTypeRequest.Type.INFANT) {
                for (TicketTypeRequest ticketTypeRequest : listEntry.getValue()) {
                    infantCount += ticketTypeRequest.getNoOfTickets();
                    isInfantTicketFlag=true;
                }
            }
        }
        return validateTicketRequest(infantCount,adultCount,isAdultTicketFlag,isChildTicketFlag,isInfantTicketFlag);
    }

    /**
     *
     * @param infantCount - total Infant ticket Count
     * @param adultCount - total Infant adult Count
     * @return flag based on the condition
     */
    private boolean validateTicketRequest(int infantCount, int adultCount,boolean isAdultTicketFlag,
                                          boolean isChildTicketFlag,boolean isInfantTicketFlag) {

        if((isAdultTicketFlag && adultCount<=0) && (!isChildTicketFlag && !isInfantTicketFlag)){
            throw new InvalidPurchaseException("Please select atleast one adult ticket.");
        } else if(!isAdultTicketFlag && (isChildTicketFlag || isInfantTicketFlag)){
            throw new InvalidPurchaseException("An infant or child ticket cannot be proceed without adult.");
        } else if((isAdultTicketFlag && isChildTicketFlag && isInfantTicketFlag) && (adultCount<infantCount) ){
            throw new InvalidPurchaseException("An infant and adult ticket does not match.");
        }else if((isAdultTicketFlag && isInfantTicketFlag) && infantCount>adultCount){
            throw new InvalidPurchaseException("Please add adult ticket by " + (infantCount - adultCount)+".");
        }
        else {
            return true;
        }


    }

    /**
     * Method to group the ticket request by ticketType
     *
     * @param ticketTypeRequests - input Request
     * @return grouped ticket
     */
    private Map<TicketTypeRequest.Type, List<TicketTypeRequest>> groupTicketByTicketType(TicketTypeRequest... ticketTypeRequests) {
        return Arrays.stream(ticketTypeRequests)
                .collect(Collectors.groupingBy(TicketTypeRequest::getTicketType));
    }

}
