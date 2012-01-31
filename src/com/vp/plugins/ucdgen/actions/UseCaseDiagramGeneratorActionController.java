package com.vp.plugins.ucdgen.actions;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.ViewManager;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.itb.informatika.nlp.UseCaseDiagramElementExtractor;

public class UseCaseDiagramGeneratorActionController implements VPActionController {

	private String TUR;

	@Override
	public void performAction(VPAction action) {

		// getting the diagram manager and the model element factory
		DiagramManager diagramManager = ApplicationManager.instance().getDiagramManager();
		IModelElementFactory factory = IModelElementFactory.instance();

		// create a new use case diagram
		createUsecaseDiagram(diagramManager, factory);
	}

	@Override
	public void update(VPAction action) {
		//
	}

	private void setTUR(String contentTUR) {
		this.TUR = contentTUR;
	}

	public String getTUR() {
		return TUR;
	}

	public void loadTUR() {
		ViewManager viewManager = ApplicationManager.instance().getViewManager();
		Component parentFrame = viewManager.getRootFrame();

		// Load TUR file
		JFileChooser fileChooser = viewManager.createJFileChooser();
		fileChooser.setFileFilter(new FileFilter() {

			@Override
			public String getDescription() {
				return "*.txt";
			}

			@Override
			public boolean accept(File file) {
				return file.isDirectory() || file.getName().toLowerCase().endsWith(".txt");
			}
		});

		fileChooser.showOpenDialog(parentFrame);
		File file = fileChooser.getSelectedFile();

		if (file != null && !file.isDirectory()) {
			String contentTUR = "";
			String line = "";

			try {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				while ((line = reader.readLine()) != null) {
					contentTUR += line;
					contentTUR += '\n';
				}
				setTUR(contentTUR);
				reader.close();

			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	}

	private String toTitleCase(String input) {
		char[] tmp = input.toCharArray();
		tmp[0] = Character.toTitleCase(tmp[0]);
		return new String(tmp, 0, tmp.length);
	}

	public void createUsecaseDiagram(DiagramManager manager, IModelElementFactory factory) {
		loadTUR();

		Map<String, IUseCase> useCaseMap = new HashMap<String, IUseCase>();
		Map<String, IShapeUIModel> useCaseShapeMap = new HashMap<String, IShapeUIModel>();

		UseCaseDiagramElementExtractor ucdExtor = new UseCaseDiagramElementExtractor();
		ucdExtor.extractUseCaseDiagramElement(TUR);
		Map<String, List<String>> ucdeAssociation = ucdExtor.getAssociation();

		// create and open a new use case diagram
		IUseCaseDiagramUIModel diagram = (IUseCaseDiagramUIModel) manager.createDiagram(DiagramManager.DIAGRAM_TYPE_USE_CASE_DIAGRAM);
		manager.openDiagram(diagram);

		for (Map.Entry<String, List<String>> entry : ucdeAssociation.entrySet()) {

			IActor actor = factory.createActor();
			actor.setName(toTitleCase(entry.getKey()));
			IShapeUIModel actorShape = ((IShapeUIModel) manager.createDiagramElement(diagram, actor));
			actorShape.resetCaption();

			for (String action : entry.getValue()) {
				IUseCase usecase = null;
				IShapeUIModel usecaseShape = null;
				if (useCaseMap.containsKey(action)) {
					usecase = useCaseMap.get(action);
					usecaseShape = useCaseShapeMap.get(action);
				} else {
					usecase = factory.createUseCase();
					usecase.setName(action);
					usecaseShape = ((IShapeUIModel) manager.createDiagramElement(diagram, usecase));
					usecaseShape.fitSize();

					useCaseMap.put(action, usecase);
					useCaseShapeMap.put(action, usecaseShape);
				}

				IAssociation association = factory.createAssociation();
				association.getFromEnd().setModelElement(usecase);
				association.getToEnd().setModelElement(actor);

				manager.createConnector(diagram, association, usecaseShape, actorShape, null);
			}
		}

		manager.layout(diagram, DiagramManager.LAYOUT_HIERARCHIC);

	}
}
