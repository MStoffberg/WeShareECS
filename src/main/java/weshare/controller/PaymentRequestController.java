package weshare.controller;

import io.javalin.http.Handler;
import weshare.model.*;
import weshare.persistence.ExpenseDAO;
import weshare.persistence.PersonDAO;
import weshare.server.Routes;
import weshare.server.ServiceRegistry;
import weshare.server.WeShareServer;
import javax.money.MonetaryAmount;
import java.time.LocalDate;
import java.util.*;

import static weshare.model.MoneyHelper.ZERO_RANDS;

public class PaymentRequestController {

    /**
     * Handler for viewing a specific payment request by expense ID.
     * Retrieves the corresponding expense and any associated payment requests.
     * The view is rendered using the "paymentrequest.html" template.
     */
    public static final Handler viewPaymentRequest = context -> {
        String expenseId = context.queryParam("expenseId");
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);

        // Fetch the expense associated with the given expense ID
        Optional<Expense> optionalExpense = expensesDAO.get(UUID.fromString(expenseId));
        Expense expense = optionalExpense.get();

        // Get payment requests sent by the logged-in person
        Collection<PaymentRequest> requests = expensesDAO.findPaymentRequestsSent(personLoggedIn);

        // View model to pass data to the view template
        Map<String, Object> viewModel = Map.of(
                "expense", expense,
                "requests", requests
        );

        // Render the payment request page
        context.render("paymentrequest.html", viewModel);
    };

    /**
     * Handler for viewing the payment requests sent by the logged-in user.
     * Calculates the total of all payment requests and passes it to the view.
     */
    public static final Handler viewSent = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        // Retrieve all payment requests sent by the logged-in person
        Collection<PaymentRequest> requests = expensesDAO.findPaymentRequestsSent(personLoggedIn);

        // Calculate the grand total of the amounts requested
        MonetaryAmount grandTotal = requests.stream()
                .map(PaymentRequest::getAmountToPay)
                .reduce(ZERO_RANDS, MonetaryAmount::add);

        // View model for rendering the sent requests page
        Map<String, Object> viewModel = Map.of(
                "requests", requests,
                "grandTotal", grandTotal
        );

        // Render the sent payment requests page
        context.render("paymentrequest_sent.html", viewModel);
    };

    /**
     * Handler for viewing payment requests received by the logged-in user.
     * It retrieves the total amount owed and renders the view.
     */
    public static final Handler viewReceived = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        // Retrieve all payment requests received by the logged-in person
        Collection<PaymentRequest> paymentRequests = expensesDAO.findPaymentRequestsReceived(personLoggedIn);

        // Calculate the total amount requested
        MonetaryAmount grandTotal = paymentRequests.stream()
                .map(PaymentRequest::getAmountToPay)
                .reduce(ZERO_RANDS, MonetaryAmount::add);

        // View model to pass the data to the template
        Map<String, Object> viewModel = Map.of(
                "PaymentRequest", paymentRequests,
                "grandTotal", grandTotal
        );

        // Render the received payment requests page
        context.render("paymentrequest_received.html", viewModel);
    };

    /**
     * Handler for sending a payment request for a specific expense.
     * It retrieves the form parameters (expense ID, recipient email, amount, and due date),
     * creates a new PaymentRequest, and saves it to the database.
     */
    public static Handler sendPaymentRequest = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        // Retrieve form parameters
        String expenseId = context.formParam("expense_id");
        String email = context.formParam("email");
        String amount = context.formParam("amountRequested");
        LocalDate dueDate = LocalDate.parse(context.formParam("dueDate"), DateHelper.DD_MM_YYYY);

        // Fetch the associated expense by ID
        Expense expense = expensesDAO.get(UUID.fromString(expenseId)).get();

        // Create a new person based on the email provided in the form
        Person personWhoShouldPayBack = new Person(email);
        MonetaryAmount amountToPay = MoneyHelper.amountOf(Long.parseLong(amount));

        // Create and save the new payment request
        PaymentRequest paymentRequest = new PaymentRequest(expense, personWhoShouldPayBack, amountToPay, dueDate);
        expense.requestPayment(personWhoShouldPayBack, paymentRequest.getAmountToPay(), dueDate);
        expensesDAO.save(expense);

        // Redirect to the payment request view for the specified expense
        context.redirect("/paymentrequest?expenseId=" + expenseId);
    };

    /**
     * Handler for paying a payment request.
     * The handler retrieves the form parameters (expense ID and payment request ID),
     * processes the payment, and saves the updated expense.
     */
    public static Handler payPaymentRequest = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        // Retrieve form parameters
        UUID expenseId = UUID.fromString(context.formParam("expenseId"));
        UUID paymentRequestId = UUID.fromString(context.formParam("paymentRequestId"));

        // Fetch the expense and process the payment request
        Expense expense1 = expensesDAO.get(expenseId).get();
        MonetaryAmount amountPaid = expense1.totalAmountOfPaymentsRequested();
        Expense expense2 = new Expense(personLoggedIn, expense1.getDescription(), amountPaid, expense1.getDate());

        // Mark the payment request as paid
        expense1.payPaymentRequest(paymentRequestId, personLoggedIn, LocalDate.now());

        // Save both the original expense and the updated expense
        expensesDAO.save(expense1);
        expensesDAO.save(expense2);

        // Redirect to the received payment requests page
        context.redirect(Routes.RECEIVED);
    };
}
