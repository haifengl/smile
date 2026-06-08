import java.util.Map;
import smile.deep.Dataset;
import smile.deep.Loss;
import smile.deep.Model;
import smile.deep.Optimizer;
import smile.deep.layer.*;
import smile.deep.layer.Layer;
import smile.deep.layer.SequentialBlock;
import smile.deep.metric.Accuracy;
import smile.deep.metric.Averaging;
import smile.deep.metric.Precision;
import smile.deep.metric.Recall;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;

//--- CELL ---
Device device = Device.preferredDevice();

Model net = new Model(new SequentialBlock(
        Layer.relu(784, 64, 0.5),
        Layer.relu(64, 32),
        Layer.logSoftmax(32, 10)),
        input -> input.reshape(input.size(0), 784)
).to(device);

Dataset data = Dataset.mnist("deep/src/test/resources/data/mnist", true, 64);
Dataset test = Dataset.mnist("deep/src/test/resources/data/mnist", false, 64);

// Instantiate an SGD optimization algorithm to update our model's parameters.
Optimizer optimizer = Optimizer.SGD(net, 0.01);
Loss loss = Loss.nll();
net.train(10, optimizer, loss, data, test, null, new Accuracy(),
        new Precision(Averaging.Micro),
        new Precision(Averaging.Macro),
        new Precision(Averaging.Weighted),
        new Recall(Averaging.Micro),
        new Recall(Averaging.Macro),
        new Recall(Averaging.Weighted));

try (var guard = Tensor.noGradGuard()) {
    Map<String, Double> metrics = net.eval(test,
            new Accuracy(),
            new Precision(Averaging.Micro),
            new Precision(Averaging.Macro),
            new Precision(Averaging.Weighted),
            new Recall(Averaging.Micro),
            new Recall(Averaging.Macro),
            new Recall(Averaging.Weighted));
    for (var entry : metrics.entrySet()) {
        System.out.format("Testing %s = %.2f%%\n", entry.getKey(), 100 * entry.getValue());
    }
    IO.println("accuracy = " + metrics.get("Accuracy"));
    IO.println("micro-precision = " + metrics.get("Micro-Precision"));
    IO.println("micro-recall = " + metrics.get("Micro-Recall"));
    IO.println("weighted-recall = " + metrics.get("Weighted-Recall"));
}

// Serialize the model as a checkpoint.
net.save("mnist.pt");

// Loads the model from checkpoint.
Model model = new Model(new SequentialBlock(
        Layer.relu(784, 64, 0.5),
        Layer.relu(64, 32),
        Layer.logSoftmax(32, 10)),
        input -> input.reshape(input.size(0), 784)
);

model.load("mnist.pt").to(device).eval();
var accuracy = model.eval(test, new Accuracy()).get("Accuracy");
IO.println("accuracy = " + accuracy);

